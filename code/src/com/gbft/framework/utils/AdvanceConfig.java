package com.gbft.framework.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;

/**
 * only supports framework config, does not support protocol specific config
 */
public class AdvanceConfig {
    private static Map<String, AdvanceConfigField> configFields = new HashMap<>();

    private static void recursiveDetect(YamlNode node, YamlMapping parent, List<String> path) {
        var mappings = parent.yamlMapping(node);

        // leaf node
        if (mappings == null) return;

        var advanceTag = mappings.string("advance");
        // advance feature
        if (advanceTag != null && advanceTag.equals("true")) {
            var propertyName = String.join(".", path);
            configFields.put(propertyName, new AdvanceConfigField(propertyName));
            return;
        }

        // recurse
        for (var child : mappings.keys()) {
            path.add(child.asScalar().value());
            recursiveDetect(child, mappings, path);
            path.remove(path.size() - 1);
        }
    }

    public static void load(String content) throws IOException {
        var mapping = Yaml.createYamlInput(content).readYamlMapping();

        for (var node : mapping.keys()) {
            var path = new ArrayList<String>();
            path.add(node.asScalar().value());
            recursiveDetect(node, mapping, path);
        }
    }
    
    public static String string(String property) {
        if (!configFields.containsKey(property)) return Config.string(property);
        return configFields.get(property).getValue(Config::string, Config::stringList);
    }
    
    public static int integer(String property) {
        if (!configFields.containsKey(property)) return Config.integer(property);
        return configFields.get(property).getValue(Config::integer, Config::intList);
    }
    
    public static double doubleNumber(String property) {
        if (!configFields.containsKey(property)) return Config.doubleNumber(property);
        return configFields.get(property).getValue(Config::doubleNumber, Config::doubleList);
    }
    
    public static boolean bool(String property) {
        if (!configFields.containsKey(property)) return Config.bool(property);
        return configFields.get(property).getValue(Config::bool, Config::boolList);
    }
}
