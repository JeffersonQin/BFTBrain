package com.gbft.plugin.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import com.gbft.framework.core.Entity;
import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.data.RequestData.Operation;
import com.gbft.framework.plugins.MessagePlugin;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.DataUtils;

public class ReadOnlyMessagePlugin implements MessagePlugin {

    private Entity entity;
    // reqnum -> value -> counter
    private Map<Long, Map<Integer, LongAdder>> readOnlyMatchings;
    private ConcurrentLinkedQueue<RequestData> pendingReadOnlyRequests;
    private Map<Long, Integer> executionResults;

    private static int quorumSize = Config.integer("general.f") * 2 + 1;
    private static int nodeCount = Config.integer("general.f") * 3 + 1;

    private Object pendingLock = new Object();

    public ReadOnlyMessagePlugin(Entity entity) {
        this.entity = entity;
        this.readOnlyMatchings = new ConcurrentHashMap<>();
        this.pendingReadOnlyRequests = new ConcurrentLinkedQueue<>();
        this.executionResults = new ConcurrentHashMap<>();
    }

    @Override
    public MessageData processIncomingMessage(MessageData message) {
        if (message.getRequestsList() == null || message.getRequestsList().size() == 0) return message;
        if (entity.isClient()) {
            // client side.
            // since server's read only replies are blocked
            // they must be always read-only or not read-only
            var read_only = message.getRequestsList().stream().allMatch(req -> req.getOperationValue() == Operation.READ_ONLY_VALUE);
            if (!read_only) return message;
            // update server response
            for (var reply : message.getReplyDataMap().entrySet()) {
                var quorum = readOnlyMatchings.get(reply.getKey());
                // request not yet finished
                if (quorum != null) {
                    // update value
                    quorum.computeIfAbsent(reply.getValue(), k -> new LongAdder()).increment();

                    var resp = quorum.entrySet().parallelStream()
                            .filter(entry -> (entry.getValue().longValue() >= quorumSize))
                            .map(entry -> entry.getKey()).findAny();
                    // check if quorum exists
                    if (resp.isPresent()) {
                        // update benchmark
                        this.entity.benchmarkManager.recordClientReadOnly(message.getRequestsList(), System.nanoTime());
                        // clear field
                        readOnlyMatchings.remove(reply.getKey());
                        // System.out.println("read only success.");
                    }
                    // otherwise check if all responses received 
                    else {
                        // read only failed, clean garbage to prevent OOM
                        // remove quorum in the map to enable automatic GC of quorum
                        if (quorum.values().stream().mapToLong(LongAdder::longValue).sum() == nodeCount) {
                            readOnlyMatchings.remove(reply.getKey());
                            // System.out.println("read only failed.");
                        } else {
                            // System.out.println("read only pending.");
                        }
                    }
                }
            }
            return DataUtils.invalidate(message);
        } else {
            // server side.
            // client will only send one by one, 
            // thus all requests must be always read-only or always not read-only
            var read_only = message.getRequestsList().stream().allMatch(req -> req.getOperationValue() == Operation.READ_ONLY_VALUE);
            if (!read_only) return message;
            // instant execution
            for (var request : message.getRequestsList()) {
                this.executionResults.put(request.getRequestNum(), this.entity.dataset.execute(request));
            }
            // batching
            var blockSize = Config.integer("benchmark.block-size");

            for (var request : message.getRequestsList()) {
                this.pendingReadOnlyRequests.offer(request);
            }

            var block = new ArrayList<RequestData>(blockSize);
            var replies = new HashMap<Long, Integer>();

            synchronized (pendingLock) {
                if (this.pendingReadOnlyRequests.size() < blockSize) {
                    return DataUtils.invalidate(message);
                }
    
                for (var i = 0; i < blockSize; i ++) {
                    var req = pendingReadOnlyRequests.remove();
                    var reqNum = req.getRequestNum();
                    block.add(req);
                    replies.put(reqNum, executionResults.remove(reqNum));
                }
            }

            var targets = List.copyOf(block.stream().map(r -> r.getClient()).collect(Collectors.toSet()));
            var sendBlockMessage = DataUtils.createMessage(null, 0, StateMachine.REPLY, entity.getId(), targets, null, block, replies, DataUtils.getDigest(block));

            entity.sendMessage(entity.processMessage(sendBlockMessage));

            return DataUtils.invalidate(message);
        }
    }

    @Override
    public MessageData processOutgoingMessage(MessageData message) {
        if (entity.isClient()) {
            for (var request : message.getRequestsList()) {
                if (request.getOperationValue() == Operation.READ_ONLY_VALUE) {
                    readOnlyMatchings.put(request.getRequestNum(), new ConcurrentHashMap<>());
                }
            }
            return message;
        } else {
            return message;
        }
    }
    
}
