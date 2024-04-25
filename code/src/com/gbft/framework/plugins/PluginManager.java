package com.gbft.framework.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.gbft.framework.core.Entity;
import com.gbft.framework.utils.Config;
import com.gbft.framework.utils.Printer;
import com.gbft.framework.utils.Printer.Verbosity;
import com.gbft.plugin.message.CheckpointMessagePlugin;
import com.gbft.plugin.message.DigestMessagePlugin;
import com.gbft.plugin.message.LearningMessagePlugin;
import com.gbft.plugin.message.MacMessagePlugin;
import com.gbft.plugin.message.ReadOnlyMessagePlugin;
import com.gbft.plugin.message.SpeculateMessagePlugin;
import com.gbft.plugin.pipeline.DirectPipelinePlugin;
import com.gbft.plugin.pipeline.QcPipelinePlugin;
import com.gbft.plugin.role.BasicPrimaryPlugin;
import com.gbft.plugin.role.PrimaryPassivePlugin;
import com.gbft.plugin.role.PrimaryQcPlugin;
import com.gbft.plugin.transition.CheckpointTransitionPlugin;

public class PluginManager {

    public static Map<String, Function<Entity, RolePlugin>> rolePlugins = new HashMap<>();
    public static Map<String, Function<Entity, MessagePlugin>> messagePlugins = new HashMap<>();
    public static Map<String, Function<Entity, PipelinePlugin>> pipelinePlugins = new HashMap<>();
    public static Map<String, Function<Entity, TransitionPlugin>> transitionPlugins = new HashMap<>();

    public static void registerRolePlugin(String id, Function<Entity, RolePlugin> generator) {
        rolePlugins.put(id, generator);
    }

    public static RolePlugin getRolePlugin(Entity entity) {
        var name = Config.string("plugins.role");
        // use passive role plugin if cheapbft is included
        if (Config.bool("general.learning")) {
            name = "passive";
        }
        if (Config.list("switching.debug-sequence") != null && 
            Config.list("switching.debug-sequence").contains("cheapbft")) {
            name = "passive";
        }
        if (!rolePlugins.containsKey(name)) {
            Printer.print(Verbosity.V, "{plugin-manager} ", "Role plugin not found: " + name);
            return null;
        }
        return rolePlugins.get(name).apply(entity);
    }

    public static void registerMessagePlugin(String id, Function<Entity, MessagePlugin> generator) {
        messagePlugins.put(id, generator);
    }

    public static List<MessagePlugin> getMessagePlugins(Entity entity) {
        var list = Config.list("plugins.message");
        // add speculate message plugin if zyzzyva is included
        if (Config.bool("general.learning") || (Config.list("switching.debug-sequence") != null 
                && Config.list("switching.debug-sequence").contains("zyzzyva"))) {
            if (!list.contains("speculate")) {
                list.add(0, "speculate");
            }
        }
        if (Config.bool("general.learning")) {
            if (!list.contains("learning")) {
                list.add("learning");
            }
        }
        var plugins = new ArrayList<MessagePlugin>();
        for (var item : list) {
            if (messagePlugins.containsKey(item)) {
                plugins.add(messagePlugins.get(item).apply(entity));
            } else {
                Printer.print(Verbosity.V, "{plugin-manager} ", "Message plugin not found: " + item);
            }
        }

        return plugins;
    }

    public static void registerPipelinePlugin(String id, Function<Entity, PipelinePlugin> generator) {
        pipelinePlugins.put(id, generator);
    }

    public static PipelinePlugin getPipelinePlugin(Entity entity) {
        var name = Config.string("plugins.pipeline");
        return pipelinePlugins.get(name).apply(entity);
    }

    public static void registerTransitionPlugin(String id, Function<Entity, TransitionPlugin> generator) {
        transitionPlugins.put(id, generator);
    }

    public static List<TransitionPlugin> getTransitionPlugins(Entity entity) {
        var list = Config.list("plugins.transition");
        var plugins = new ArrayList<TransitionPlugin>();
        for (var item : list) {
            if (transitionPlugins.containsKey(item)) {
                plugins.add(transitionPlugins.get(item).apply(entity));
            } else {
                Printer.print(Verbosity.V, "{plugin-manager} ", "Transition plugin not found: " + item);
            }
        }

        return plugins;
    }

    public static void initDefaultPlugins() {
        PluginManager.registerRolePlugin("primary", (entity) -> new BasicPrimaryPlugin(entity));
        PluginManager.registerRolePlugin("passive", (entity) -> new PrimaryPassivePlugin(entity));
        PluginManager.registerRolePlugin("qc-primary", (entity) -> new PrimaryQcPlugin(entity));

        PluginManager.registerMessagePlugin("checkpoint", (entity) -> new CheckpointMessagePlugin(entity));
        PluginManager.registerMessagePlugin("digest", (entity) -> new DigestMessagePlugin(entity));
        PluginManager.registerMessagePlugin("mac", (entity) -> new MacMessagePlugin(entity));
        // PluginManager.registerMessagePlugin("nil", (entity) -> new NilMessagePlugin(entity));
        PluginManager.registerMessagePlugin("speculate", (entity) -> new SpeculateMessagePlugin(entity));
        PluginManager.registerMessagePlugin("read-only", (entity) -> new ReadOnlyMessagePlugin(entity));
        PluginManager.registerMessagePlugin("learning", (entity) -> new LearningMessagePlugin(entity));

        PluginManager.registerTransitionPlugin("checkpoint", (entity) -> new CheckpointTransitionPlugin(entity));

        PluginManager.registerPipelinePlugin("direct", (entity) -> new DirectPipelinePlugin(entity));
        PluginManager.registerPipelinePlugin("qc-pipeline", (entity) -> new QcPipelinePlugin(entity));
    }
}
