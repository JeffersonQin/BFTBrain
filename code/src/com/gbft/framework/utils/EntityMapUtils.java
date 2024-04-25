package com.gbft.framework.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gbft.framework.data.UnitData;

public class EntityMapUtils {

    private static List<UnitData> unitDataList;

    private static List<Integer> units;
    private static List<Integer> nodes;
    private static List<Integer> clients;
    private static Map<Integer, List<Integer>> unitNodes;
    private static Map<Integer, List<Integer>> unitClients;
    private static Map<Integer, Integer> entityUnitMap;

    public static void addUnitData(UnitData data) {
        var unit = data.getUnit();

        units.add(unit);
        unitNodes.put(unit, new ArrayList<>());
        unitClients.put(unit, new ArrayList<>());
        unitDataList.add(data);

        var runner = nodes.size() + clients.size();
        for (var i = 0; i < data.getClientCount(); i++) {
            clients.add(runner);
            unitClients.get(unit).add(runner);
            entityUnitMap.put(runner, unit);
            runner += 1;
        }

        for (var i = 0; i < data.getNodeCount(); i++) {
            nodes.add(runner);
            unitNodes.get(unit).add(runner);
            entityUnitMap.put(runner, unit);
            runner += 1;
        }
    }

    public static int unitCount() {
        return units.size();
    }

    public static int nodeCount() {
        return nodes.size();
    }

    public static int clientCount() {
        return clients.size();
    }

    public static List<Integer> getAllUnits() {
        return units;
    }

    public static List<Integer> getAllNodes() {
        return nodes;
    }

    public static List<Integer> getAllClients() {
        return clients;
    }

    public static List<Integer> getUnitNodes(int unit) {
        return unitNodes.get(unit);
    }

    public static List<Integer> getUnitClients(int unit) {
        return unitClients.get(unit);
    }

    public static int getNodeIndex(int nodeId) {
        return nodes.indexOf(nodeId);
    }

    public static int getNodeId(int index) {
        return nodes.get(index);
    }

    public static int getUnit(int entity) {
        return entityUnitMap.get(entity);
    }

    public static List<UnitData> allUnitData() {
        return unitDataList;
    }

    static {
        units = new ArrayList<>();
        nodes = new ArrayList<>();
        clients = new ArrayList<>();
        unitNodes = new HashMap<>();
        unitClients = new HashMap<>();
        entityUnitMap = new HashMap<>();
        unitDataList = new ArrayList<>();
    }
}
