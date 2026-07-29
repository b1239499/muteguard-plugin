package com.fkc.muteguard;

import org.bukkit.plugin.java.JavaPlugin;

public final class MuteGuardPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        MuteTracker tracker = new MuteTracker();

        getServer().getPluginManager().registerEvents(new MuteStateListener(tracker), this);
        getServer().getPluginManager().registerEvents(new MuteGuardListener(this, tracker), this);

        getLogger().info("MuteGuard 已啟用，被禁言的玩家現在也無法使用私訊指令。");
    }
}
