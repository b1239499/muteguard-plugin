package com.fkc.muteguard;

import org.bukkit.plugin.java.JavaPlugin;

public final class MuteGuardPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new MuteGuardListener(this), this);
        getLogger().info("MuteGuard 已啟用，被禁言的玩家現在也無法使用私訊指令。");
    }
}
