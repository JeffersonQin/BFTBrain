package com.gbft.framework.statemachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.amihaiemil.eoyaml.Node;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlNode;
import com.gbft.framework.statemachine.Transition.UpdateMode;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.Timekeeper;

public class StateMachine {

    // Special Protocol Phase
    public static int NORMAL_PHASE;

    // Special Protocol States
    public static int ANY_STATE;
    public static int EXECUTED;
    public static final Set<String> specialStates = Set.of("executed", "any");

    // Special Protocol Roles
    // TODO: Move to role plugin?
    public static int CLIENT;
    public static int NODE;
    public static int PRIMARY;

    // Special Protocol Message
    public static int REQUEST;
    public static int REPLY;
    public static final Set<String> specialMessages = Set.of("request", "reply", "report", "checkpoint", "fetch");

    // Config

    public static List<String> roles;
    public static List<String> phases;
    public static List<StateInfo> states;
    public static List<MessageInfo> messages;

    // TODO: Use record
    public static class StateInfo {
        public int phase;
        public String name;
        public List<Integer> messages = new ArrayList<>();
        // role -> list(transition)
        public Map<Integer, List<Transition>> transitions = new HashMap<>();
    }

    // TODO: Use record
    public static class MessageInfo {
        public Set<Integer> phases;
        public String name;
        public boolean hasRequestBlock;
    }

    public static void init() {
        // TODO: Validate config
        // Set roles according to CheapBFT since it covers all the roles needed
        Config.setCurrentProtocol("cheapbft");
        roles = Config.list("protocol.roles");
        phases = new ArrayList<>();
        states = new ArrayList<>();
        messages = new ArrayList<>();

        // Load all the phases, states, messages from our protocol pool
        for (var protocol : Config.getProtocols()) {
            Config.setCurrentProtocol(protocol);
            var prefix = protocol + "_";

            for (var phaseConfig : Config.sequence("protocol.phases")) {
                var phaseMapping = phaseConfig.asMapping();
                var phaseName = phaseMapping.string("name");
                if (!phases.contains(phaseName)) {
                    phases.add(phaseName);
                }

                if (phaseMapping.yamlSequence("states") != null) {
                    for (var stateConfig : phaseMapping.yamlSequence("states")) {
                        var stateInfo = new StateInfo();
                        stateInfo.phase = phases.indexOf(phaseName);
                        stateInfo.name = Config.string(stateConfig);
                        if (!specialStates.contains(stateInfo.name)) {
                            stateInfo.name = prefix + stateInfo.name;
                        }
                        states.add(stateInfo);
                    }
                }

                for (var messageConfig : phaseMapping.yamlSequence("messages")) {
                    var messageInfo = new MessageInfo();
                    if (messageConfig.type() == Node.SCALAR) {
                        messageInfo.name = Config.string(messageConfig);
                    } else {
                        var mm = messageConfig.asMapping();
                        messageInfo.name = mm.string("name");
                        messageInfo.hasRequestBlock = mm.string("request-block").equals("true");
                    }
                    if (!specialMessages.contains(messageInfo.name)) {
                        messageInfo.name = prefix + messageInfo.name;
                    }

                    var op = messages.stream().filter(x -> x.name.equals(messageInfo.name)).findAny();
                    if (op.isEmpty()) {
                        messageInfo.phases = new HashSet<>();
                        messageInfo.phases.add(phases.indexOf(phaseName));
                        messages.add(messageInfo);
                    } else {
                        var existing = op.get();
                        existing.phases.add(phases.indexOf(phaseName));
                    }
                }
            }

            for (var transitionConfig : Config.sequence("protocol.transitions.from")) {
                var transitionMapping = transitionConfig.asMapping();

                var role = roles.indexOf(transitionMapping.string("role"));
                var fromState = states.indexOf(findState(transitionMapping.string("state"), prefix));

                for (var to : transitionMapping.yamlSequence("to")) {
                    var condition = genCondition(to, prefix);
                    if (condition == null) {
                        continue;
                    }

                    var transition = genTransition(to.asMapping(), fromState, condition, prefix);
                    states.get(fromState).transitions.computeIfAbsent(role, r -> new ArrayList<>()).add(transition);
                }
            }   
        }

        NORMAL_PHASE = phases.indexOf("normal");
        try {
            ANY_STATE = states.indexOf(findState("any"));
        } catch (NoSuchElementException e) {
            ANY_STATE = -1;
        }
        EXECUTED = states.indexOf(findState("executed"));
        NODE = roles.indexOf("nodes");
        CLIENT = roles.indexOf("client");
        PRIMARY = roles.indexOf("primary");
        REQUEST = messages.indexOf(findMessage("request"));
        REPLY = messages.indexOf(findMessage("reply"));
    }

    private static Condition genCondition(YamlNode node, String prefix) {
        Condition condition = null;
        var conditionConfig = node.asMapping().yamlMapping("condition");
        if (conditionConfig == null) {
            return new Condition(Condition.TRUE_CONDITION, Map.of());
        }

        var mapping = conditionConfig.asMapping();
        var type = mapping.string("type");
        if (type == null || type.isBlank()) {
            condition = new Condition(Condition.TRUE_CONDITION, Map.of());
        } else if (type.equals("message")) {
            var message = findMessage(conditionConfig.string("message"), prefix);
            if (message == null) {
                System.out.println("Message not defined in config: " + conditionConfig.string("message"));
                return null;
            }

            var msgtype = messages.indexOf(message);
            var quorum = parseQuorum(conditionConfig.string("quorum"));
            var params = Map.of(Condition.MESSAGE_TYPE, msgtype, Condition.QUORUM, quorum);
            condition = new Condition(Condition.MESSAGE_CONDITION, params);
        } else if (type.equals("timeout")) {
            var mode = conditionConfig.string("mode").equals("sequence") ? Timekeeper.SEQUENCE_MODE : Timekeeper.STATE_MODE;
            var multiplier = conditionConfig.integer("multiplier");
            var params = Map.of(Condition.TIMEOUT_MODE, mode, Condition.TIMEOUT_MULTIPLIER, multiplier > 0 ? multiplier : 1);
            condition = new Condition(Condition.TIMEOUT_CONDITION, params);
        }

        return condition;
    }

    private static Transition genTransition(YamlMapping transitionMapping, int fromState, Condition condition, String prefix) {
        var toState = states.indexOf(findState(transitionMapping.string("state"), prefix));
        var updateMode = transitionMapping.string("update") == null ? UpdateMode.NONE
                : UpdateMode.valueOf(transitionMapping.string("update").toUpperCase());

        var responseList = new ArrayList<Pair<Integer, Integer>>();
        var responsesConfig = transitionMapping.yamlSequence("response");
        if (responsesConfig != null) {
            for (var responseConfig : responsesConfig) {
                var rm = responseConfig.asMapping();
                var target = roles.indexOf(rm.string("target"));
                var message = messages.indexOf(findMessage(rm.string("message"), prefix));
                states.get(fromState).messages.add(message);
                responseList.add(Pair.of(target, message));
            }
        }

        var extraTallyList = new ArrayList<Pair<Integer, Integer>>();
        var extraTallyConfig = transitionMapping.yamlSequence("extra_tally");
        if (extraTallyConfig != null) {
            for (var extraTally : extraTallyConfig) {
                var em = extraTally.asMapping();
                var message = messages.indexOf(findMessage(em.string("message"), prefix));
                var role = roles.indexOf(em.string("role"));
                extraTallyList.add(Pair.of(role, message));
            }
        }

        return new Transition(fromState, toState, condition, updateMode, responseList, extraTallyList);
    }

    private static Pattern pattern = Pattern.compile("^(\\d*)(\\D*)([+-](\\d*))?$");

    public static int parseQuorum(String str) {
        str = str.replaceAll(" ", "");
        var m = pattern.matcher(str);

        if (m.find()) {
            if (isEmpty(m.group(2)) && isEmpty(m.group(4))) {
                return Integer.parseInt(str);
            }

            var multiplier = 1;
            var variable = 1;
            var constant = 0;

            if (!isEmpty(m.group(1))) {
                multiplier = Integer.parseInt(m.group(1));
            }

            if (!isEmpty(m.group(2))) {
                variable = Config.integer("protocol.general." + m.group(2));
                if (variable == -1) {
                    variable = Config.integer("general." + m.group(2));
                }
            }

            if (!isEmpty(m.group(4))) {
                constant = Integer.parseInt(m.group(4));
                if (m.group(3).startsWith("-")) {
                    constant = constant * -1;
                }
            }

            return multiplier * variable + constant;
        }

        return 0;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    public static MessageInfo findMessage(String name, String prefix) {
        var _name = specialMessages.contains(name) ? name : (prefix + name);
        var result = messages.stream().filter(m -> m.name.equals(_name)).findAny();
        return result.isEmpty() ? null : result.get();
    }

    public static MessageInfo findMessage(String name) {
        return findMessage(name, "");
    }

    public static StateInfo findState(String name, String prefix) {
        var _name = specialStates.contains(name) ? name : (prefix + name);
        var result = states.stream().filter(m -> m.name.equals(_name)).findAny();
        return result.isEmpty() ? null : result.get();
    }

    public static StateInfo findState(String name) {
        return findState(name, "");
    }

    public static Transition findTransition(int from, int role) {
        var stateInfo = states.get(from);
        var candidates = stateInfo.transitions.get(role);
        var match = candidates.parallelStream().findAny();
        return match.isEmpty() ? null : match.get();
    }

    public static boolean isIdle(int currentState) {
        return states.get(currentState).name.contains("idle");
    }
}
