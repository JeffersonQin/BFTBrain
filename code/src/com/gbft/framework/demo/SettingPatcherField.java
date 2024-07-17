package com.gbft.framework.demo;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingPatcherField {
    private String type;
    private String value;
    private String key;

    public SettingPatcherField(String type, String value, String key) {
        this.type = type;
        this.value = value;
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        if (type.equals("string")) {
            return value;
        } else if (type.equals("int")) {
            return Integer.parseInt(value);
        } else if (type.equals("double")) {
            return Double.parseDouble(value);
        } else if (type.equals("boolean")) {
            if (value.equals("true") || value.equals("True") || value.equals("TRUE") || value.equals("1")) {
                return true;
            } else {
                return false;
            }
        } else if (type.equals("int_list")) {
            // sep by ','
            var list = value.split(",");
            var int_list = new ArrayList<Integer>();
            for (var i = 0; i < list.length; i++) {
                int_list.add(Integer.parseInt(list[i]));
            }
            return int_list;
        } else if (type.equals("double_list")) {
            // sep by ','
            var list = value.split(",");
            var double_list = new ArrayList<Double>();
            for (var i = 0; i < list.length; i++) {
                double_list.add(Double.parseDouble(list[i]));
            }
            return double_list;
        } else if (type.equals("string_list")) {
            // sep by ','
            var list = value.split(",");
            var string_list = new ArrayList<String>();
            for (var i = 0; i < list.length; i++) {
                string_list.add(list[i]);
            }
            return string_list;
        } else {
            return null;
        }
    }

    public String getKey() {
        return key;
    }
}
