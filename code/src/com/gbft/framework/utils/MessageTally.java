package com.gbft.framework.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.gbft.framework.data.MessageData;
import com.gbft.framework.data.RequestData;
import com.gbft.framework.statemachine.StateMachine;
import com.gbft.framework.utils.Printer.Verbosity;
import com.google.protobuf.ByteString;

public class MessageTally {

    // seqnum -> message-type -> view-num -> digest -> set(node)
    protected Map<Long, Map<Integer, ConcurrentSkipListMap<Long, Map<ByteString, Set<Integer>>>>> counter;

    // sequence-num -> message-type -> view-num
    protected Map<Long, Map<QuorumId, ConcurrentSkipListSet<Long>>> quorumMessages;

    // sequence-num -> view-num -> digest
    protected Map<Long, ConcurrentSkipListMap<Long, ByteString>> quorumDigests;

    protected Map<ByteString, List<RequestData>> candidateBlocks;
    protected Map<ByteString, Map<Long, Integer>> candidateReplies;

    protected ReadLock counterReadLock;
    protected WriteLock counterWriteLock;
    protected ReadLock quorumReadLock;
    protected WriteLock quorumWriteLock;

    public MessageTally() {
        counter = new ConcurrentHashMap<>();
        quorumMessages = new ConcurrentHashMap<>();
        quorumDigests = new ConcurrentHashMap<>();
        candidateBlocks = new ConcurrentHashMap<>();
        candidateReplies = new ConcurrentHashMap<>();

        var counterLock = new ReentrantReadWriteLock();
        counterReadLock = counterLock.readLock();
        counterWriteLock = counterLock.writeLock();

        var quorumLock = new ReentrantReadWriteLock();
        quorumReadLock = quorumLock.readLock();
        quorumWriteLock = quorumLock.writeLock();
    }

    public void tally(MessageData message) {
        var seqnum = message.getSequenceNum();
        var viewnum = message.getViewNum();
        var digest = message.getDigest();
        var source = message.getSource();
        var type = message.getMessageType();

        counterWriteLock.lock();

        if (StateMachine.messages.get(type).hasRequestBlock) {
            candidateBlocks.put(digest, message.getRequestsList());
        }

        if (!message.getReplyDataMap().isEmpty()) {
            candidateReplies.put(digest, message.getReplyDataMap());
        }

        counter.computeIfAbsent(seqnum, s -> new ConcurrentHashMap<>())
               .computeIfAbsent(type, t -> new ConcurrentSkipListMap<>())
               .computeIfAbsent(viewnum, v -> new ConcurrentHashMap<>())
               .computeIfAbsent(digest, d -> ConcurrentHashMap.newKeySet())
               .add(source);

        counterWriteLock.unlock();
    }

    public Long getMaxQuorum(long seqnum, QuorumId quorumId) {
        Long max = null;
        counterReadLock.lock();
        var subcounter = DataUtils.nestedGet(counter, seqnum, quorumId.message); // subcounter.key = view
        if (subcounter == null) {
            counterReadLock.unlock();
            return null;
        }

        quorumReadLock.lock();
        var subquorum = DataUtils.nestedGet(quorumMessages, seqnum, quorumId);
        var currentmax = subquorum == null ? -1L : subquorum.last();
        if (subcounter.lastKey() <= currentmax) {
            max = subquorum.last();
            quorumReadLock.unlock();
            counterReadLock.unlock();

            if (Printer.verbosity >= Verbosity.VVVVV) {
                Printer.print(Verbosity.VVVVV, "GetMaxQuorum Success [currentmax]: ", StateMachine.messages.get(quorumId.message).name.toUpperCase() + " seqnum: " + seqnum + " size: " + quorumId.quorum);
            }

            return max;
        }
        quorumReadLock.unlock();

        quorumWriteLock.lock();
        for (var viewnum : subcounter.tailMap(currentmax, false).descendingKeySet()) {
            var digest = subcounter.get(viewnum).entrySet().parallelStream()
                                   .filter(entry -> entry.getValue().size() >= quorumId.quorum)
                                   .map(entry -> entry.getKey()).findAny();
            if (digest.isPresent()) {
                updateQuorumDigest(seqnum, viewnum, digest.get(), quorumId);
                max = viewnum;

                if (Printer.verbosity >= Verbosity.VVVVV) {
                    var q = DataUtils.nestedGet(subcounter, max, digest.get());
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    for(Integer x : q) {
                        sb.append(Integer.toString(x));
                        sb.append(" ");
                    }
                    String st = sb.toString();
                    st = st.trim();
                    st = st + "]";

                    Printer.print(Verbosity.VVVVV, "GetMaxQuorum Success [filter]: ", StateMachine.messages.get(quorumId.message).name.toUpperCase() + " seqnum: " + seqnum + " size: " + quorumId.quorum + " quorum: " + st);
                }
                break;
            }
        }
        quorumWriteLock.unlock();
        counterReadLock.unlock();

        return max;
    }

    public boolean hasQuorum(long seqnum, long viewnum, QuorumId quorumId) {
        counterReadLock.lock();
        var subcounter = DataUtils.nestedGet(counter, seqnum, quorumId.message); // subcounter.key = view
        if (subcounter == null || !subcounter.containsKey(viewnum)) {
            counterReadLock.unlock();
            return false;
        }

        quorumReadLock.lock();
        var subquorum = DataUtils.nestedGet(quorumMessages, seqnum, quorumId);
        if (subquorum != null && subquorum.contains(viewnum)) {
            quorumReadLock.unlock();
            counterReadLock.unlock();
            return true;
        }
        quorumReadLock.unlock();

        quorumWriteLock.lock();
        var viewcounter = subcounter.get(viewnum);
        var digest = viewcounter.entrySet().parallelStream()
                                .filter(entry -> entry.getValue().size() >= quorumId.quorum)
                                .map(entry -> entry.getKey()).findAny();

        var match = digest.isPresent();
        if (match) {
            updateQuorumDigest(seqnum, viewnum, digest.get(), quorumId);
        }
        quorumWriteLock.unlock();
        counterReadLock.unlock();

        return match;
    }

    public void updateQuorumBlock(long seqnum, long viewnum, ByteString digest, List<RequestData> block, QuorumId quorumId) {
        quorumWriteLock.lock();
        candidateBlocks.put(digest, block);
        updateQuorumDigest(seqnum, viewnum, digest, quorumId);
        quorumWriteLock.unlock();
    }

    private void updateQuorumDigest(long seqnum, long viewnum, ByteString digest, QuorumId quorumId) {
        quorumDigests.computeIfAbsent(seqnum, s -> new ConcurrentSkipListMap<>()).put(viewnum, digest);
        quorumMessages.computeIfAbsent(seqnum, s -> new ConcurrentHashMap<>())
                      .computeIfAbsent(quorumId, c -> new ConcurrentSkipListSet<>()).add(viewnum);
    }

    public Set<Integer> getQuorumNodes(long seqnum, long viewnum, QuorumId quorumId) {
        quorumReadLock.lock();
        var digest = getQuorumDigest(seqnum, viewnum);
        quorumReadLock.unlock();

        counterReadLock.lock();
        var nodes = counter.get(seqnum).get(quorumId.message).get(viewnum).get(digest);
        counterReadLock.unlock();

        return nodes;
    }

    public ByteString getQuorumDigest(long seqnum, long viewnum) {
        var submap = quorumDigests.get(seqnum);
        return submap == null ? null : submap.get(viewnum);
    }

    public List<RequestData> getQuorumBlock(long seqnum, long viewnum) {
        quorumReadLock.lock();
        var submap = quorumDigests.get(seqnum);
        quorumReadLock.unlock();
        return submap == null ? null : candidateBlocks.get(submap.get(viewnum));
    }

    public Map<Long, Integer> getQuorumReplies(long seqnum, long viewnum) {
        var submap = quorumDigests.get(seqnum);
        return submap == null ? null : candidateReplies.get(submap.get(viewnum));
    }

    public Long getMaxQuorum(long seqnum) {
        var submap = quorumDigests.get(seqnum);
        return submap == null ? null : submap.lastKey();
    }

    public static class QuorumId {
        public int message;
        public int quorum;

        public QuorumId(int message, int quorum) {
            this.message = message;
            this.quorum = quorum;
        }

        @Override
        public int hashCode() {
            return Objects.hash(message, quorum);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            QuorumId other = (QuorumId) obj;
            return message == other.message && quorum == other.quorum;
        }
    }
}
