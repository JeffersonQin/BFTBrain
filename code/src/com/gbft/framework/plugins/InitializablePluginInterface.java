package com.gbft.framework.plugins;

import com.gbft.framework.data.PluginData;

public interface InitializablePluginInterface {
    public void handleInitEvent(PluginData data);

    public boolean isInitialized();
}
