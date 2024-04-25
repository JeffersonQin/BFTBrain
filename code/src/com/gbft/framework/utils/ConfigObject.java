package com.gbft.framework.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;
import com.amihaiemil.eoyaml.YamlSequence;

public class ConfigObject {
    private Map<String, Object> cache = new ConcurrentHashMap<>();
    private List<YamlMapping> mappings = new ArrayList<>();

    public ConfigObject(String framework, String protocol) throws IOException {
        mappings.add(Yaml.createYamlInput(framework).readYamlMapping());
        mappings.add(Yaml.createYamlInput(protocol).readYamlMapping());
    }

    private List<String> _list(String property) {
        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);

        var list = new ArrayList<String>();
        var mapping = mappingFor(prefix, postfix);
        if (mapping != null) {
            var seq = mapping.yamlSequence(postfix);
            for (var item : seq) {
                list.add(item.asScalar().value());
            }
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    public List<String> stringList(String property) {
        if (cache.containsKey(property)) {
            return (List<String>) cache.get(property);
        }
        
        var ret = _list(property);
        cache.put(property, ret);
        return ret;
    }

    /**
     * Alias of <code>stringList</code>, added for compatibility
     * @param property property to find
     * @return list of string
     */
    public List<String> list(String property) {
        return stringList(property);
    }

    @SuppressWarnings("unchecked")
    public List<Integer> intList(String property) {
        if (cache.containsKey(property)) {
            return (List<Integer>) cache.get(property);
        }
        
        var ret = _list(property).stream().map(s -> Integer.parseInt(s)).collect(Collectors.toList());
        cache.put(property, ret);
        return ret;
    }

    @SuppressWarnings("unchecked")
    public List<Double> doubleList(String property) {
        if (cache.containsKey(property)) {
            return (List<Double>) cache.get(property);
        }
        
        var ret = _list(property).stream().map(s -> Double.parseDouble(s)).collect(Collectors.toList());
        cache.put(property, ret);
        return ret;
    }

    @SuppressWarnings("unchecked")
    public List<Boolean> boolList(String property) {
        if (cache.containsKey(property)) {
            return (List<Boolean>) cache.get(property);
        }
        
        var ret = _list(property).stream().map(s -> Boolean.parseBoolean(s)).collect(Collectors.toList());
        cache.put(property, ret);
        return ret;
    }

    public YamlSequence sequence(String property) {
        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);
        return mappingFor(prefix, postfix).yamlSequence(postfix);
    }

    public YamlMapping mapping(String property) {
        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);
        return mappingFor(prefix, postfix);
    }

    public String string(String property) {
        if (cache.containsKey(property)) {
            return (String) cache.get(property);
        }

        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);

        var mapping = mappingFor(prefix, postfix);

        if (mapping == null) {
            System.err.println("Config value not found: " + property);
            cache.put(property, "");
        } else {
            var value = mapping.string(postfix);
            cache.put(property, value);
        }

        return (String) cache.get(property);
    }

    public String string(YamlNode node) {
        return node.asScalar().value();
    }

    public int integer(String property) {
        if (cache.containsKey(property)) {
            return (int) cache.get(property);
        }

        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);

        var mapping = mappingFor(prefix, postfix);
        if (mapping == null) {
            System.err.println("Config value not found: " + property);
            cache.put(property, -1);
        } else {
            var value = mapping.integer(postfix);
            cache.put(property, value);
        }

        return (int) cache.get(property);
    }

    public double doubleNumber(String property) {
        if (cache.containsKey(property)) {
            return (double) cache.get(property);
        }

        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);

        var mapping = mappingFor(prefix, postfix);
        if (mapping == null) {
            System.err.println("Config value not found: " + property);
            cache.put(property, -1.0);
        } else {
            var value = mapping.doubleNumber(postfix);
            cache.put(property, value);
        }

        return (double) cache.get(property);
    }

    public boolean bool(String property) {
        if (cache.containsKey(property)) {
            return (boolean) cache.get(property);
        }

        var point = property.lastIndexOf('.');
        var prefix = property.substring(0, point);
        var postfix = property.substring(point + 1);

        var mapping = mappingFor(prefix, postfix);

        if (mapping == null) {
            System.err.println("Config value not found: " + property);
            cache.put(property, false);
        } else {
            var value = mapping.string(postfix).equals("true");
            cache.put(property, value);
        }

        return (boolean) cache.get(property);
    }

    private YamlMapping mappingFor(String prefix, String postfix) {
        var list = prefix.split("\\.");

        for (var mapping : mappings) {
            var runner = mapping;
            for (var item : list) {
                runner = runner.yamlMapping(item);
                if (runner == null) {
                    break;
                }
            }

            if (runner != null && runner.value(postfix) != null) {
                return runner;
            }
        }

        return null;
    }
}
