package com.gbft.framework.utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;
import com.amihaiemil.eoyaml.YamlSequence;

public class Config {

    private static Map<String, ConfigObject> configs = new HashMap<>();

    private static String currentProtocol;

    public static void load(Map<String, String> configContents, String defaultProtocol) throws IOException {
        var framework = configContents.get("framework");
        for (var entry : configContents.entrySet()) {
            if (entry.getKey().equals("framework")) continue;
            configs.put(entry.getKey(), new ConfigObject(framework, entry.getValue()));
        }
        setCurrentProtocol(defaultProtocol);
    }

    public static Set<String> getProtocols() {
        return configs.keySet();
    }

    public static String getCurrentProtocol() {
        return currentProtocol;
    }

    public static void setCurrentProtocol(String currentProtocol) {
        Config.currentProtocol = currentProtocol;
    }

    public static List<String> stringList(String property) {
        return configs.get(currentProtocol).stringList(property);
    }

    /**
     * Alias of <code>stringList</code>, added for compatibility
     * @param property property to find
     * @return list of string
     */
    public static List<String> list(String property) {
        return stringList(property);
    }

    public static List<Integer> intList(String property) {
        return configs.get(currentProtocol).intList(property);
    }

    public static List<Double> doubleList(String property) {
        return configs.get(currentProtocol).doubleList(property);
    }

    public static List<Boolean> boolList(String property) {
        return configs.get(currentProtocol).boolList(property);
    }

    public static YamlSequence sequence(String property) {
        return configs.get(currentProtocol).sequence(property);
    }

    public static YamlMapping mapping(String property) {
        return configs.get(currentProtocol).mapping(property);
    }

    public static String string(String property) {
        return configs.get(currentProtocol).string(property);
    }

    public static String string(YamlNode node) {
        return node.asScalar().value();
    }

    public static int integer(String property) {
        return configs.get(currentProtocol).integer(property);
    }

    public static double doubleNumber(String property) {
        return configs.get(currentProtocol).doubleNumber(property);
    }

    public static boolean bool(String property) {
        return configs.get(currentProtocol).bool(property);
    }

}
