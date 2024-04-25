import gbft_pb2_grpc
import gbft_pb2
import logging
import numpy as np
import pandas as pd
import sklearn 
import argparse
import grpc
import queue
import random
import time
import csv
import os
from datetime import datetime
from concurrent import futures
from threading import Condition, Lock
from sklearn.ensemble import RandomForestRegressor, GradientBoostingRegressor
from constants import *

parser = argparse.ArgumentParser(description='Start a learning agent.')
parser.add_argument('--unit', '-u', type=int, required=True, help='Unit id of the corresponding bedrock entity')
parser.add_argument('--port', '-p', type=int, required=True, help='Port of the corresponding bedrock entity')
parser.add_argument('--episodes', '-e', type=int, default=10000, help="Number of episodes to be run [Optional]")
parser.add_argument('--model', '-m', type=str, default="random-forest", help="Predictive model to use [Optional]: random-forest, or neural-network")
parser.add_argument('--num-models', '-n', type=str, default="quadratic", help="Number of models to have [Optional]: multi, single, quadratic, adapt, adapt+, or heuristic")
parser.add_argument('--discard', '-d', type=int, default=5, help="Number of warm up episodes to discard, in order to avoid polluting experience buffer [Optional]")
parser.add_argument('--replay-buffer', '-r', type=int, default=-1, help="Limit of size of replay buffer, less than 1 will mean no limit. [Optional]: -1 as no limit")
parser.add_argument('--multi-onehot', '-o', type=bool, default=False, help="Whether to use one-hot encoding for multi model [Optional]")
args = parser.parse_args()

request_queue = queue.Queue()
protocol_pool = ["pbft", "zyzzyva", "cheapbft", "sbft", "hotstuff", "prime"]


def reward_engineering(reward: float) -> float:
    if reward < 1000:
        return (reward / 1000) ** 2 * 1000
    return reward


class AgentCommServicer(gbft_pb2_grpc.AgentCommServicer):

    def send_data(self, request, context):
        request_queue.put(request)

        return gbft_pb2.google_dot_protobuf_dot_empty__pb2.Empty()


def get_replay_buffer_length(experiences_y):
    if args.replay_buffer <= 0:
        return len(experiences_y)
    return min(args.replay_buffer, len(experiences_y))

def init_quadratic_experience_buffer(experiences_X, experiences_y):
    # read all csv files in the checkpoint folder in sequence according to their timestamp
    folder_path = "checkpoint/" 
    files = sorted([f for f in os.listdir(folder_path) if f.endswith(".csv")])

    # initialize the experience buffer (the order of training data is preserved)
    for file in files:
        df = pd.read_csv(folder_path + file)
        df.rename(columns=str.strip, inplace=True)
        for (previous_action, action), group in df.groupby(['previous_action', 'action']):
            states = group.loc[:, 'FAST_PATH_FREQUENCY':'HAS_LEADER_ROTATION'].values
            rewards = group['throughput'].values
            experiences_X[str.strip(previous_action)][str.strip(action)].extend([states[i, :] for i in range(states.shape[0])])
            experiences_y[str.strip(previous_action)][str.strip(action)].extend([reward_engineering(reward) for reward in rewards])

def init_multi_experience_buffer(experiences_X, experiences_y):
    # read all csv files in the checkpoint folder in sequence according to their timestamp
    folder_path = "checkpoint/" 
    files = sorted([f for f in os.listdir(folder_path) if f.endswith(".csv")])

    # initialize the experience buffer (the order of training data is preserved)
    for file in files:
        df = pd.read_csv(folder_path + file)
        df.rename(columns=str.strip, inplace=True)
        for action, group in df.groupby('action'):
            if args.multi_onehot:
                states = group.loc[:, 'FAST_PATH_FREQUENCY':'previous_action'].values

                # -3 because to exclude HAS_FAST_PATH and HAS_LEADER_ROTATION, prev_action
                onehotted_states = np.zeros((states.shape[0], states.shape[1] + len(protocol_pool) - 3))
                onehotted_states[:, :states.shape[1] - 3] = states[:, :-3]

                for i, state in enumerate(states):
                    onehotted_states[i][states.shape[1] + protocol_pool.index(state[-1].strip()) - 3] = 1

                print("TRAINING CHECKPOINT SHAPE:", onehotted_states.shape)

                rewards = group['throughput'].values
                experiences_X[str.strip(action)].extend([onehotted_states[i, :] for i in range(states.shape[0])])
                experiences_y[str.strip(action)].extend([reward_engineering(reward) for reward in rewards])
            else:
                states = group.loc[:, 'FAST_PATH_FREQUENCY':'HAS_LEADER_ROTATION'].values
                rewards = group['throughput'].values
                experiences_X[str.strip(action)].extend([states[i, :] for i in range(states.shape[0])])
                experiences_y[str.strip(action)].extend([reward_engineering(reward) for reward in rewards])

def init_single_experience_buffer(experiences_X, experiences_y, actions_matrix):
    # read all csv files in the checkpoint folder in sequence according to their timestamp
    folder_path = "checkpoint/" 
    files = sorted([f for f in os.listdir(folder_path) if f.endswith(".csv")])   
    actions_matrix_copy = actions_matrix.copy()

    # initialize the experience buffer (the order of training data is preserved)
    for file in files:
        df = pd.read_csv(folder_path + file)
        df.rename(columns=str.strip, inplace=True)
        # one-hot encoding for actions
        df['action'] = df['action'].apply(lambda a: protocol_pool.index(str.strip(a)))
        actions = actions_matrix_copy[df['action'].values]
        # states
        states = df.loc[:, 'FAST_PATH_FREQUENCY':'HAS_LEADER_ROTATION'].values
        # rewards
        rewards = df['throughput'].values
        # experiences_X = (state, action)
        states_actions = np.hstack((states, actions))
        experiences_X.extend([states_actions[i, :] for i in range(states_actions.shape[0])])
        experiences_y.extend([reward_engineering(reward) for reward in rewards])


def init_adapt_experience_buffer(experiences_X, experiences_y, actions_matrix):
    # read all csv files in the checkpoint folder in sequence according to their timestamp
    folder_path = "checkpoint/" 
    files = sorted([f for f in os.listdir(folder_path) if f.endswith(".csv")])   
    actions_matrix_copy = actions_matrix.copy()

    # initialize the experience buffer (the order of training data is preserved)
    for file in files:
        df = pd.read_csv(folder_path + file)
        df.rename(columns=str.strip, inplace=True)
        # one-hot encoding for actions
        df['action'] = df['action'].apply(lambda a: protocol_pool.index(str.strip(a)))
        actions = actions_matrix_copy[df['action'].values]
        # states
        # only use request size as feature
        states = df.loc[:, 'REQUEST_SIZE':'REQUEST_SIZE'].values
        # rewards
        rewards = df['throughput'].values
        # experiences_X = (state, action)
        states_actions = np.hstack((states, actions))
        experiences_X.extend([states_actions[i, :] for i in range(states_actions.shape[0])])
        experiences_y.extend([reward_engineering(reward) for reward in rewards])


class QuadraticRF:
    # 6 * 6 models and experience buckets: (prev_action, action)
    def __init__(self):
        self.experiences_X = {prev_action: {} for prev_action in protocol_pool}
        self.experiences_y = {prev_action: {} for prev_action in protocol_pool}
        self.models = {prev_action: {} for prev_action in protocol_pool}
        for prev_action in protocol_pool:
            for action in protocol_pool:
                self.experiences_X[prev_action][action] = []
                self.experiences_y[prev_action][action] = []
                self.models[prev_action][action] = RandomForestRegressor(max_depth=5)
        self.last_updated_bucket = None
        self.on_hold_buckets = {}
        # init experience buffer
        init_quadratic_experience_buffer(self.experiences_X, self.experiences_y)
        # init models
        for prev_action in self.models:
            for action in self.models[prev_action]:
                if len(self.experiences_y[prev_action][action]):
                    self.train(prev_action, action)

    def record_reward(self, prev_action, action, reward):
        self.experiences_y[prev_action][action].append(reward_engineering(reward))
        self.last_updated_bucket = (prev_action, action)
        if (prev_action, action) in self.on_hold_buckets:
            del self.on_hold_buckets[(prev_action, action)]

    def record_state_and_action(self, current_protocol, best_protocol, state):
        self.experiences_X[current_protocol][best_protocol].append(state)
    
    def get_prev_state(self, prev_prev_action, prev_action):
        return self.experiences_X[prev_prev_action][prev_action][len(self.experiences_y[prev_prev_action][prev_action]) - 1].tolist()
    
    def train(self, prev_action, action):
        replay_length = get_replay_buffer_length(self.experiences_y[prev_action][action])
        bootstrapped_idx = np.random.choice(replay_length, replay_length, replace=True)
        training_X = np.vstack(self.experiences_X[prev_action][action])[-replay_length:][bootstrapped_idx, :]
        training_y = np.array(self.experiences_y[prev_action][action])[-replay_length:][bootstrapped_idx]
        self.models[prev_action][action].fit(training_X, training_y)
    
    def retrain_and_predict(self, state, prev_action):
        # retrain and inference
        training_overhead = 0
        inference_overhead = 0
        predictions = {}

        # retrain the bucket that has been updated
        if self.last_updated_bucket:
            training_start = time.time()
            self.train(self.last_updated_bucket[0], self.last_updated_bucket[1])
            training_overhead += round(time.time() - training_start, 6)

        # inference
        for action in self.models[prev_action]:
            # if the experience bucket has no data, we should explore this bucket
            training_size = len(self.experiences_y[prev_action][action])
            if training_size == 0:
                predictions[action] = float('inf')
                # on hold the buckets that are currently being explored
                if (prev_action, action) in self.on_hold_buckets:
                    predictions[action] = float('-inf')
            else:
                inference_start = time.time()
                prediction = self.models[prev_action][action].predict(state.reshape(1, -1))
                predictions[action] = np.max(prediction)
                inference_overhead += round(time.time() - inference_start, 6)

        # choose the best action and break the tie randomly
        best_prediction = max(predictions.values())
        best_protocols = [key for key, value in predictions.items() if value == best_prediction]
        best_protocol = random.choice(best_protocols)
        self.on_hold_buckets[(prev_action, best_protocol)] = True

        return best_protocol, training_overhead, inference_overhead

class MultiRF:

    def __init__(self):
        self.experiences_X = {}
        self.experiences_y = {}
        self.models = {}
        for protocol in protocol_pool:
            self.experiences_X[protocol] = []
            self.experiences_y[protocol] = []
            self.models[protocol] = RandomForestRegressor(max_depth=5)
        self.last_updated_bucket = None
        # init experience buffer
        init_multi_experience_buffer(self.experiences_X, self.experiences_y)
        # init models
        for model_name in self.models:
            if len(self.experiences_y[model_name]):
                self.train(model_name)
        

    def record_reward(self, prev_action, action, reward):
        self.experiences_y[action].append(reward_engineering(reward))
        self.last_updated_bucket = action
    
    def onehot_state(self, state, current_protocol):
        # -2 because to exclude HAS_FAST_PATH and HAS_LEADER_ROTATION
        oh_state = np.zeros((state.shape[0] + len(protocol_pool) - 2))
        oh_state[:state.shape[0] - 2] = state[:-2]
        oh_state[state.shape[0] + protocol_pool.index(current_protocol) - 2] = 1
        return oh_state
    
    def record_state_and_action(self, current_protocol, best_protocol, state):
        if args.multi_onehot:
            self.experiences_X[best_protocol].append(self.onehot_state(state, current_protocol))
        else:
            self.experiences_X[best_protocol].append(state)
    
    def get_prev_state(self, prev_prev_action, prev_action):
        if args.multi_onehot:
            ret = self.experiences_X[prev_action][len(self.experiences_y[prev_action]) - 1].tolist()
            # remove the trailing onehot, add back HAS_FAST_PATH and HAS_LEADER_ROTATION
            ret = ret[:-(len(protocol_pool) - 2)]
            # set to meaningless value
            ret[-1] = -1
            ret[-2] = -1
            return ret
        else:
            return self.experiences_X[prev_action][len(self.experiences_y[prev_action]) - 1].tolist()
    
    def train(self, model_name):
        replay_length = get_replay_buffer_length(self.experiences_y[model_name])
        bootstrapped_idx = np.random.choice(replay_length, replay_length, replace=True)
        training_X = np.vstack(self.experiences_X[model_name])[-replay_length:][bootstrapped_idx, :]
        training_y = np.array(self.experiences_y[model_name])[-replay_length:][bootstrapped_idx]
        self.models[model_name].fit(training_X, training_y)
    
    def retrain_and_predict(self, state, prev_action):
        # retrain and inference
        training_overhead = 0
        inference_overhead = 0
        predictions = {}
        for model_name in self.models:
            # if the experience bucket has no data, we should explore this bucket
            training_size = len(self.experiences_y[model_name])
            if training_size == 0:
                predictions[model_name] = float('inf')
            else:
                # retrain the bucket that has been updated
                if model_name == self.last_updated_bucket:
                    training_start = time.time()
                    self.train(model_name)
                    training_overhead += round(time.time() - training_start, 6)
                # inference
                inference_start = time.time()
                if args.multi_onehot:
                    prediction = self.models[model_name].predict(self.onehot_state(state, prev_action).reshape(1, -1))
                else:
                    prediction = self.models[model_name].predict(state.reshape(1, -1))
                predictions[model_name] = np.max(prediction)
                inference_overhead += round(time.time() - inference_start, 6)

        # choose the best action and break the tie randomly
        best_prediction = max(predictions.values())
        best_protocols = [key for key, value in predictions.items() if value == best_prediction]
        best_protocol = random.choice(best_protocols)

        return best_protocol, training_overhead, inference_overhead

class SingleRF:

    def __init__(self):
        self.experiences_X = []
        self.experiences_y = []
        self.model = RandomForestRegressor(max_depth=5)
        # TODO: featurize action space instead of using one-hot encoding
        actions_matrix = np.eye(len(protocol_pool))
        # (state, action) --> reward
        self.enumeration_matrix = np.hstack((np.zeros((actions_matrix.shape[0], 6)), actions_matrix))
        # init experience buffer
        init_single_experience_buffer(self.experiences_X, self.experiences_y, actions_matrix)
        # init model
        if len(self.experiences_y):
            self.train()

    def record_reward(self, prev_action, action, reward):
        self.experiences_y.append(reward_engineering(reward))
    
    def record_state_and_action(self, current_protocol, best_protocol, state):
        # get index of best_protocol
        best_protocol_index = protocol_pool.index(best_protocol)
        self.experiences_X.append(self.enumeration_matrix[best_protocol_index, :])

    def get_prev_state(self, prev_prev_action, prev_action):
        return self.experiences_X[len(self.experiences_y) - 1][:6].tolist()

    def train(self):
        replay_length = get_replay_buffer_length(self.experiences_y)
        bootstrapped_idx = np.random.choice(replay_length, replay_length, replace=True)
        training_X = np.vstack(self.experiences_X)[-replay_length:][bootstrapped_idx, :]
        training_y = np.array(self.experiences_y)[-replay_length:][bootstrapped_idx]
        self.model.fit(training_X, training_y)
    
    def retrain_and_predict(self, state, prev_action):
        training_overhead = 0
        inference_overhead = 0
        self.enumeration_matrix[:, 0:6] = state
        if len(self.experiences_y):
            training_start = time.time()
            # retrain the model if there is any training data
            self.train()
            training_overhead += round(time.time() - training_start, 6)
            # inference
            inference_start = time.time()
            prediction = self.model.predict(self.enumeration_matrix)
            inference_overhead += round(time.time() - inference_start, 6)

            # break the tie randomly
            best_protocol_index = np.random.choice(np.flatnonzero(np.isclose(prediction, prediction.max())), replace=True)
            best_protocol = protocol_pool[best_protocol_index]
        else:
            # choose a random protocol if there is no training data
            best_protocol = random.choice(protocol_pool)

        return best_protocol, training_overhead, inference_overhead

class ADAPT:

    def __init__(self):
        self.experiences_X = []
        self.experiences_y = []
        self.model = RandomForestRegressor(max_depth=5)
        # TODO: featurize action space instead of using one-hot encoding
        actions_matrix = np.eye(len(protocol_pool))
        # (state, action) --> reward
        # ONLY REQ_SIZE AS FEATURE
        self.enumeration_matrix = np.hstack((np.zeros((actions_matrix.shape[0], 1)), actions_matrix))
        # init experience buffer
        init_adapt_experience_buffer(self.experiences_X, self.experiences_y, actions_matrix)
        # init model
        if len(self.experiences_y):
            self.train()

    def record_reward(self, prev_action, action, reward):
        pass
    
    def record_state_and_action(self, current_protocol, best_protocol, state):
        pass

    def get_prev_state(self, prev_prev_action, prev_action):
        return None

    def train(self):
        training_X = np.vstack(self.experiences_X)
        training_y = np.array(self.experiences_y)
        self.model.fit(training_X, training_y)
    
    def retrain_and_predict(self, state, prev_action):
        # only use request size as feature
        self.enumeration_matrix[:, 0] = state[2]
        print("INPUT MATRIX:", self.enumeration_matrix)
        prediction = self.model.predict(self.enumeration_matrix)

        best_protocol_index = np.random.choice(np.flatnonzero(np.isclose(prediction, prediction.max())), replace=True)
        best_protocol = protocol_pool[best_protocol_index]

        return best_protocol, 0, 0
    

class ADAPT_PLUS:
    # 6 * 6 models and experience buckets: (prev_action, action)
    def __init__(self):
        self.experiences_X = {prev_action: {} for prev_action in protocol_pool}
        self.experiences_y = {prev_action: {} for prev_action in protocol_pool}
        self.models = {prev_action: {} for prev_action in protocol_pool}
        for prev_action in protocol_pool:
            for action in protocol_pool:
                self.experiences_X[prev_action][action] = []
                self.experiences_y[prev_action][action] = []
                self.models[prev_action][action] = RandomForestRegressor(max_depth=5)
        # init experience buffer
        init_quadratic_experience_buffer(self.experiences_X, self.experiences_y)
        # init models
        for prev_action in self.models:
            for action in self.models[prev_action]:
                if len(self.experiences_y[prev_action][action]):
                    self.train(prev_action, action)

    def record_reward(self, prev_action, action, reward):
        pass

    def record_state_and_action(self, current_protocol, best_protocol, state):
        pass
    
    def get_prev_state(self, prev_prev_action, prev_action):
        return self.experiences_X[prev_prev_action][prev_action][len(self.experiences_y[prev_prev_action][prev_action]) - 1].tolist()
    
    def train(self, prev_action, action):
        training_X = np.vstack(self.experiences_X[prev_action][action])
        training_y = np.array(self.experiences_y[prev_action][action])
        self.models[prev_action][action].fit(training_X, training_y)
    
    def retrain_and_predict(self, state, prev_action):
        # retrain and inference
        training_overhead = 0
        inference_overhead = 0
        predictions = {}

        # inference
        for action in self.models[prev_action]:
            inference_start = time.time()
            prediction = self.models[prev_action][action].predict(state.reshape(1, -1))
            predictions[action] = np.max(prediction)
            inference_overhead += round(time.time() - inference_start, 6)

        # choose the best action and break the tie randomly
        best_prediction = max(predictions.values())
        best_protocols = [key for key, value in predictions.items() if value == best_prediction]
        best_protocol = random.choice(best_protocols)

        return best_protocol, training_overhead, inference_overhead

class Heuristic:
    # 6 * 6 models and experience buckets: (prev_action, action)
    def __init__(self):
        pass

    def record_reward(self, prev_action, action, reward):
        pass

    def record_state_and_action(self, current_protocol, best_protocol, state):
        pass
    
    def get_prev_state(self, prev_prev_action, prev_action):
        return None
    
    def train(self, prev_action, action):
        pass
    
    def retrain_and_predict(self, state, prev_action):
        # proposal slowness
        if state[1] > 20:
            return "prime", 0, 0
        return "zyzzyva", 0, 0

def run_agent(agent_stub):
    # init 
    actions = []
    time_records = []
    if args.model == "random-forest":
        if args.num_models == "single":
            model = SingleRF()
        elif args.num_models == "multi":
            model = MultiRF()
        elif args.num_models == "quadratic":
            model = QuadraticRF()
        elif args.num_models == "adapt":
            model = ADAPT()
        elif args.num_models == "adapt+":
            model = ADAPT_PLUS()
        elif args.num_models == "heuristic":
            model = Heuristic()
        else:
            logging.error('invalid number of models')
            return
    elif args.model == "neural-network":
        logging.error('neural network is not implemented yet')
        return

    # create the csv file for debugging and offline training
    folder_name = "data/"
    if not os.path.exists(folder_name):
        os.makedirs(folder_name)
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    data_store = open(folder_name + timestamp + " u" + str(args.unit) + ".csv", 'w')
    csv_writer = csv.writer(data_store)
    csv_writer.writerow(
        ['FAST_PATH_FREQUENCY', 'SLOWNESS_OF_PROPOSAL', 'REQUEST_SIZE', 'MESSAGE_PER_SLOT', 'HAS_FAST_PATH', 'HAS_LEADER_ROTATION', 
         'previous_action', 'action', 'throughput',
         'training_overhead(s)', 'inference_overhead(s)'])
    logging.info('learning agent has been initialized.')

    for episode in range(args.episodes):
        # wait for the notification from entity
        request = request_queue.get()
        data = request.report
        state = np.array([data[FAST_PATH_FREQUENCY], data[SLOWNESS_OF_PROPOSAL], data[REQUEST_SIZE], data[RECEIVED_MESSAGE_PER_SLOT], 
                          data[HAS_FAST_PATH], data[HAS_LEADER_ROTATION]])
        current_protocol = request.next_protocol # NOTE: reuse this proto field just for convenience
        logging.info('episode %d: received learning request from bedrock entity, state=%s, current_protocol=%s', episode, state, current_protocol)

        # record the reward for the previous episodes
        if len(actions) > 1:
            prev_action, action = actions.pop(0)
            time_record = time_records.pop(0)
            model.record_reward(prev_action, action, data[REWARD])
            prev_row = model.get_prev_state(prev_action, action)
            if prev_row is not None:
                prev_row = prev_row + [prev_action, action] + [data[REWARD]] + time_record
                csv_writer.writerow(prev_row)

        # discard warm up episodes
        if episode < args.discard - 1:
            # notify the entity to repeat current default protocol
            agent_stub.send_decision(gbft_pb2.LearningData(next_protocol="repeat"))
            continue

        # retrain and inference
        best_protocol, training_overhead, inference_overhead = model.retrain_and_predict(state, current_protocol)

        # record the state and action for the next episode
        actions.append((current_protocol, best_protocol))
        time_records.append([training_overhead, inference_overhead])
        model.record_state_and_action(current_protocol, best_protocol, state)

        # send back decision to the entity
        agent_stub.send_decision(gbft_pb2.LearningData(next_protocol=best_protocol))

        data_store.flush()
        

if __name__ == '__main__':
    LOG_FORMAT = '%(asctime)s - %(levelname)s - %(funcName)s:%(lineno)d - %(message)s'
    logging.basicConfig(level=logging.DEBUG, format=LOG_FORMAT)

    # set the same random seed for each peer
    random.seed(0)
    np.random.seed(0)

    # Start the grpc server and client
    bedrockRPCPort = args.port + 10
    agentPort = args.port + 20
    server_address = '[::]:{}'.format(agentPort)
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    gbft_pb2_grpc.add_AgentCommServicer_to_server(AgentCommServicer(), server)
    server.add_insecure_port(server_address)
    server.start()
    logging.info('grpc server running at %s.', server_address)

    try:
        entity_channel = grpc.insecure_channel('localhost:{}'.format(bedrockRPCPort))
        agent_stub = gbft_pb2_grpc.EntityCommStub(entity_channel)
        run_agent(agent_stub)       
    finally:
        entity_channel.close()

