package com.gbft.framework.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import com.gbft.framework.data.RequestData;
import com.gbft.framework.data.RequestData.Operation;
import com.gbft.framework.utils.AdvanceConfig;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.DataUtils;

public class ClientDataset extends Dataset {

    private int clientId;
    private Random random;
    private Map<Integer, LongAdder> lookahead;

    public ClientDataset(int clientId) {
        super();

        this.clientId = clientId;

        random = new Random();
        lookahead = new HashMap<>();
        IntStream.range(0, RECORD_COUNT)
                 .forEach(record -> lookahead.computeIfAbsent(record, x -> new LongAdder()).add(DEFAULT_VALUE));
    }

    @Override
    public void update(RequestData request, int value) {
        super.update(request, value);

        var record = request.getRecord();
        var op = request.getOperation();

        switch (op) {
        case INC:
            lookahead.get(record).increment();
            break;
        case ADD:
            lookahead.get(record).add(request.getValue());
            break;
        default:
            break;
        }
    }

    public RequestData createRequest(long reqnum) {

        var record = random.nextInt(AdvanceConfig.integer("workload.contention-level"));
        var operation = Operation.values()[random.nextInt(5)];
        int value = 0;

        switch (operation) {
        case ADD:
            value = random.nextInt(DEFAULT_VALUE);
            break;
        case SUB:
            var max = Math.min(DEFAULT_VALUE, lookahead.get(record).intValue());
            if (max <= 0) { // < 0 to fix #47
                operation = Operation.NOP;
            } else {
                value = random.nextInt(max);
                lookahead.get(record).add(-value);
            }
            break;
        case DEC:
            if (lookahead.get(record).intValue() < 1) {
                operation = Operation.NOP;
            } else {
                lookahead.get(record).decrement();
            }
            break;
        default:
            break;
        }

        // generate read only optimization
        if (Config.stringList("plugins.message").contains("read-only")) {
            if (random.nextDouble() < AdvanceConfig.doubleNumber("workload.read-only-ratio")) {
                operation = Operation.READ_ONLY;
            }
        }

        return DataUtils.createRequest(reqnum, record, operation, value, clientId);
    }

}
