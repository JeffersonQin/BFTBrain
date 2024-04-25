package com.gbft.framework.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.gbft.framework.data.RequestData;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.DataUtils;

public class Dataset {

    protected Map<Integer, AtomicInteger> records;

    public static final int DEFAULT_VALUE = 1000;
    public static final int RECORD_COUNT = Config.integer("workload.dataset-size");

    public Dataset() {
        records = DataUtils.concurrentMapWithDefaults(RECORD_COUNT, x -> new AtomicInteger(DEFAULT_VALUE));
    }

    // use this for copying service state
    public Dataset(Dataset dataset) {
        records = new TreeMap<>();
        for (var entry : dataset.records.entrySet()) {
            this.records.put(entry.getKey(), new AtomicInteger(entry.getValue().get()));
        }
    }

    public void setRecords(Map<Integer, Integer> records) {
        this.records.clear();
        for (var entry : records.entrySet()) {
            this.records.put(entry.getKey(), new AtomicInteger(entry.getValue()));
        }
    }
    
    public Map<Integer, AtomicInteger> getRecords() {
        return records;
    }

    public int execute(RequestData request) {
        var op = request.getOperation();
        var record = request.getRecord();

        // dummy computation
        if (request.getComputeFactor() > 0) {
            var dummy_counter = 0;
            var random = new Random();
            for (int i = 0; i < request.getComputeFactor(); i ++) {
                dummy_counter += random.nextInt();
            }
            try {
                OutputStream.nullOutputStream().write(dummy_counter);
            } catch (IOException e) {}
        }

        int value = 0;

        switch (op) {
        case ADD:
            value = records.get(record).addAndGet(request.getValue());
            break;
        case SUB:
            value = records.get(record).addAndGet(-request.getValue());
            break;
        case INC:
            value = records.get(record).incrementAndGet();
            break;
        case DEC:
            value = records.get(record).decrementAndGet();
            break;
        case READ_ONLY:
            value = records.get(record).get();
        default:
            value = records.get(record).get();
        }

        return value;
    }

    public void update(RequestData request, int value) {
        var record = request.getRecord();
        records.get(record).set(value);
    }

}
