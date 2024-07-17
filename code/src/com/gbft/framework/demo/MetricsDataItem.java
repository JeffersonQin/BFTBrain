package com.gbft.framework.demo;

public class MetricsDataItem {
    private String name;
    private String value;
    private int timestamp;
    private int id;

    public MetricsDataItem(String name, String value, int id) {
        this.name = name;
        this.value = value;
        this.id = id;
        this.timestamp = (int) (System.currentTimeMillis() / 1000);
    }

    public MetricsDataItem(String name, String value, int id, int timestamp) {
        this.name = name;
        this.value = value;
        this.id = id;
        this.timestamp = timestamp;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.value;
    }

    public int getId() {
        return this.id;
    }

    public int getTimestamp() {
        return this.timestamp;
    }
}
