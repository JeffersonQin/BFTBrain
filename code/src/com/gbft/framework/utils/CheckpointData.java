package com.gbft.framework.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentSkipListSet;

import com.gbft.framework.core.Dataset;
import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.Printer;
import com.gbft.framework.utils.Printer.Verbosity;

public class CheckpointData {
    private long num;
    private Entity entity;

    // seqnum -> state (state machine)
    private Map<Long, Integer> stateMap;
    private Map<Long, RequestData> requests;
    private Map<Long, List<RequestData>> requestBlocks;
    private Map<Long, NavigableSet<Long>> aggregationValues;
    private MessageTally messageTally;
    private MessageTally viewTally;
    
    // service state
    protected Dataset serviceState;

    // counter for next(i)
    protected Map<String, LongAdder> decisionMatching;
    private static int decisionQuorumSize = 1;

    // protocol
    protected AtomicReference<String> protocol = new AtomicReference<>();

    // performance
    public long beginTimestamp;
    public float throughput;

    protected Map<Long, Map<Long, Integer>> replies;

    public CheckpointData(long num, Entity entity) {
        this.num = num;
        this.entity = entity;
        stateMap = new ConcurrentHashMap<>();
        requests = new ConcurrentHashMap<>();
        requestBlocks = new ConcurrentHashMap<>();
        aggregationValues = new ConcurrentHashMap<>();
        messageTally = new MessageTally();
        viewTally = new MessageTally();
        serviceState = null;
        replies = new HashMap<>();
        decisionMatching = new ConcurrentHashMap<>();
    }

    /**
     * Clone dataset, invoked in Node.java (Node, not Client)
     * Only invoke when (seqnum+1) % checkpointSize == 0
     * @param currentServiceState current dataset
     */
    public void setServiceState(Dataset currentServiceState) {
        this.serviceState = new Dataset(currentServiceState);
    }

    public Dataset getServiceState() {
        return serviceState;
    }

    public void tally(MessageData message) {
        messageTally.tally(message);
        viewTally.tally(message.toBuilder().clearDigest().build());

        // track number of received messages per slot
        var seqnum = message.getSequenceNum();
        if (seqnum == entity.getBeginOfEpisode(seqnum)) {
            entity.getFeatureManager().count(entity.getEpisodeNum(seqnum), FeatureManager.RECEIVED_MESSAGE_PER_SLOT);
        }

        // timestamp when receiving leader proposal
        var type = message.getMessageType();
        if (StateMachine.messages.get(type).hasRequestBlock && type != StateMachine.REQUEST && type != StateMachine.REPLY) {
            entity.getFeatureManager().received(entity.getEpisodeNum(seqnum), seqnum);
        }

        if (type == StateMachine.REPLY && !message.getSwitch().getNextProtocol().isEmpty()) {
            decisionMatching.computeIfAbsent(message.getSwitch().getNextProtocol(), p -> new LongAdder()).increment();
        }

        // used to debug tally stack trace
        if (Printer.verbosity >= Verbosity.VVVVVV) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();

            var sb = new StringBuilder("");
            for (var element : stackTraceElements) {
                sb.append(" class: " + element.getClassName() + " file: " + element.getFileName() + " line: " + element.getLineNumber() + " method: " + element.getMethodName() + "\n");
            }

            Printer.print(Verbosity.VVVVVV, "Tally stacktrace ", sb.toString().trim());
        }
    }

    public void tallyDecision(String decision) {
        decisionMatching.computeIfAbsent(decision, p -> new LongAdder()).increment();
    }

    public void addAggregationValue(MessageData message) {
        var seqnum = message.getSequenceNum();
        if (!message.getAggregationValuesList().isEmpty()) {
            aggregationValues.computeIfAbsent(seqnum, k -> new ConcurrentSkipListSet<>()).addAll(message.getAggregationValuesList());
        }
    }

    public void addAggregationValue(long seqnum, Set<Long> values) {
        if (!values.isEmpty()) {
            aggregationValues.computeIfAbsent(seqnum, k -> new ConcurrentSkipListSet<>()).addAll(values);
        }
    }

    public NavigableSet<Long> getAggregationValues(long seqnum) {
        return aggregationValues.getOrDefault(seqnum, new ConcurrentSkipListSet<>());
    }

    public String getDecision() {
        Optional<String> nextProtocol;
        do {
            nextProtocol = decisionMatching.entrySet().parallelStream()
                    .filter(entry -> (entry.getValue().longValue() >= decisionQuorumSize)).map(entry -> entry.getKey())
                    .findAny();
        } while (!nextProtocol.isPresent());

        return nextProtocol.get();
    }

    public void addRequestBlock(long seqnum, List<RequestData> requestBlock) {
        requestBlock.forEach(request -> requests.put(request.getRequestNum(), request));
        requestBlocks.put(seqnum, requestBlock);
        // stateMap.put(seqnum, StateMachine.IDLE);
    }

    public RequestData getRequest(long reqnum) {
        return requests.get(reqnum);
    }

    public List<RequestData> getRequestBlock(long seqnum) {
        return requestBlocks.get(seqnum);
    }

    public void addReplies(long seqnum, Map<Long, Integer> blockReplies) {
        replies.put(seqnum, blockReplies);
    }

    public Map<Long, Integer> getReplies(long seqnum) {
        return replies.get(seqnum);
    }

    public MessageTally getMessageTally() {
        return messageTally;
    }

    public MessageTally getViewTally() {
        return viewTally;
    }

    /**
     * Set state of a seqnum to state
     * 
     * Set at `transition` and `execute` in `Entity.java`
     * Updated in `StateUpdateLoop` in `Entity.java`
     * @param seqnum sequence number
     * @param state state
     */
    public void setState(long seqnum, int state) {
        stateMap.put(seqnum, state);
    }

    /**
     * Get state of a seqnum
     * @param seqnum sequence number
     * @return state
     */
    public int getState(long seqnum) {
        if (protocol.get() == null) {
            return StateMachine.ANY_STATE;
        } else {
            return stateMap.getOrDefault(seqnum, StateMachine.states.indexOf(StateMachine.findState("idle", protocol.get() + "_")));
        }
    }

    public long getNum() {
        return num;
    }

    public void setProtocol(String protocol) {
        this.protocol.set(protocol);
    }

    public String getProtocol() {
        return protocol.get();
    }
}
