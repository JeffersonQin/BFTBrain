package com.gbft.framework.utils;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

public class AdvanceConfigField {

    private final int CONFIG_FIXED = 0;
    private final int CONFIG_UNIFORM = 1;
    private final int CONFIG_NORMAL = 2;
    private final int CONFIG_SCHEDULE = 3;

    private final int CONFIG_UNIFORM_DISCRETE = 0;
    private final int CONFIG_UNIFORM_CONTINUOUS = 1;

    private int config_type;
    private int config_uniform_type;

    // whether still init phase
    private boolean in_init = true;

    // normal + uniform
    private boolean expire = true;
    private Object lock;
    private Object current;

    // schedule
    private int schedule_index = 0;

    private String propertyName;

    private String getProperty(String name) {
        return propertyName + "." + name;
    }

    public AdvanceConfigField(String propertyName) {
        this.propertyName = propertyName;
        new Thread(() -> {
            daemon();
        }).start();
    }

    public String getPropertyName() {
        return this.propertyName;
    }

    private void daemon() {
        try {
            // initial delay
            Thread.sleep(Config.integer(getProperty("delay")));
            in_init = false;
            // check strategy
            if (Config.mapping(getProperty("fixed")) != null) {
                this.config_type = CONFIG_FIXED;
            } else if (Config.mapping(getProperty("uniform")) != null) {
                this.config_type = CONFIG_UNIFORM;
                // check uniform type
                if (Config.mapping(getProperty("uniform.discrete")) != null) {
                    this.config_uniform_type = CONFIG_UNIFORM_DISCRETE;
                } else if (Config.mapping(getProperty("uniform.continuous")) != null) {
                    this.config_uniform_type = CONFIG_UNIFORM_CONTINUOUS;
                } else {
                    System.err.println("No such advance uniform config strategy!");
                }
                // check interval
                var interval = Config.integer(getProperty("uniform.interval"));
                
                while (true) {
                    synchronized (lock) {
                        expire = true;
                    }
                    Thread.sleep(interval);
                }
            } else if (Config.mapping(getProperty("normal")) != null) {
                this.config_type = CONFIG_NORMAL;
                // check interval
                var interval = Config.integer(getProperty("normal.interval"));

                while (true) {
                    synchronized (lock) {
                        expire = true;
                    }
                    Thread.sleep(interval);
                }
            } else if (Config.mapping(getProperty("schedule")) != null) {
                this.config_type = CONFIG_SCHEDULE;
                var intervals = Config.intList(getProperty("schedule.intervals"));

                for (var i = 0; i < intervals.size(); i ++) {
                    Thread.sleep(intervals.get(i));
                    schedule_index ++;
                }
            } else {
                System.err.println("No such advance config strategy!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(Function<String, T> singletonGetter, Function<String, List<T>> listGetter) {
        // still in init
        if (in_init) {
            return singletonGetter.apply(getProperty("init"));
        }

        switch (this.config_type) {
            // fixed value strategy
            case CONFIG_FIXED:
                return singletonGetter.apply(getProperty("fixed.value"));
            // uniform distribution strategy
            case CONFIG_UNIFORM:
                switch (this.config_uniform_type) {
                    // discrete value
                    case CONFIG_UNIFORM_DISCRETE:
                        synchronized (lock) {
                            if (!expire) return (T) current;

                            var valueList = (List<T>) listGetter.apply(getProperty("uniform.discrete.values"));
                            var distList = Config.doubleList(getProperty("uniform.discrete.distribution"));

                            int index = 0;
                            double prob = new Random().nextDouble();
                            for (var dist : distList) {
                                prob -= dist;
                                if (prob <= 0) break;
                                index ++;
                            }

                            current = valueList.get(index);
                            expire = false;

                            return (T) current;
                        }
                    // continuous value
                    case CONFIG_UNIFORM_CONTINUOUS:
                        synchronized (lock) {
                            if (!expire) return (T) current;

                            var upper = Config.doubleNumber(getProperty("uniform.continuous.upper"));
                            var lower = Config.doubleNumber(getProperty("uniform.continuous.lower"));

                            double prob = new Random().nextDouble();

                            current = prob * (upper - lower) + lower;
                            expire = false;

                            return (T) current;
                        }
                    default:
                        System.err.println("No such advance config strategy!");
                }
                break;
            case CONFIG_NORMAL:
                synchronized (lock) {
                    if (!expire) return (T) current;

                    var upper = Config.doubleNumber(getProperty("normal.upper"));
                    var lower = Config.doubleNumber(getProperty("normal.lower"));
                    var mean = Config.doubleNumber(getProperty("normal.mean"));
                    var sigma = Config.doubleNumber(getProperty("normal.sigma"));

                    var val = new Random().nextGaussian(mean, sigma);
                    val = val < lower ? lower : val;
                    val = val > upper ? upper : val;

                    current = val;
                    expire = false;
                    
                    return (T) current;
                }
            case CONFIG_SCHEDULE:
                var val_list = (List<T>) listGetter.apply(getProperty("schedule.values"));
                return val_list.get(schedule_index);
            default:
                System.err.println("No such advance config strategy!");
        }
        return null;
    }
}
