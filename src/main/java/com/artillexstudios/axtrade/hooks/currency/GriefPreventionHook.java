package com.artillexstudios.axtrade.hooks.currency;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axtrade.AxTrade.HOOKS;

/**
 * Lets players trade GriefPrevention claim blocks.
 * Uses reflection so no compile-time dependency on GriefPrevention is needed.
 * Mirrors GriefPrevention's own buy/sell logic: blocks are added to / removed from the
 * player's bonus claim blocks, and only unused ("remaining") claim blocks can be traded away.
 */
public class GriefPreventionHook implements CurrencyHook {
    private Object instance = null;
    private Method getPlayerData = null;
    private Method savePlayerData = null;
    private Method getRemainingClaimBlocks = null;
    private Method getBonusClaimBlocks = null;
    private Method setBonusClaimBlocks = null;

    @Override
    public void setup() {
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Class<?> dataStoreClass = Class.forName("me.ryanhamshire.GriefPrevention.DataStore");
            Class<?> playerDataClass = Class.forName("me.ryanhamshire.GriefPrevention.PlayerData");

            instance = gpClass.getField("instance").get(null);
            getPlayerData = dataStoreClass.getMethod("getPlayerData", UUID.class);
            savePlayerData = dataStoreClass.getMethod("savePlayerData", UUID.class, playerDataClass);
            getRemainingClaimBlocks = playerDataClass.getMethod("getRemainingClaimBlocks");
            getBonusClaimBlocks = playerDataClass.getMethod("getBonusClaimBlocks");
            try {
                setBonusClaimBlocks = playerDataClass.getMethod("setBonusClaimBlocks", Integer.class);
            } catch (NoSuchMethodException ex) {
                setBonusClaimBlocks = playerDataClass.getMethod("setBonusClaimBlocks", int.class);
            }
        } catch (Exception ex) {
            instance = null;
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[AxTrade] Failed to hook into GriefPrevention: " + ex);
        }
    }

    @Override
    public String getName() {
        return "GriefPrevention";
    }

    @Override
    public Map<String, Object> getSettings() {
        return HOOKS.getSection("currencies." + getName()).getStringRouteMappedValues(true);
    }

    @Override
    public boolean worksOffline() {
        return true;
    }

    @Override
    public boolean usesDouble() {
        return false;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    private Object dataStore() throws Exception {
        return instance.getClass().getField("dataStore").get(instance);
    }

    private Object playerData(@NotNull UUID player) throws Exception {
        return getPlayerData.invoke(dataStore(), player);
    }

    // unused claim blocks, AxTrade checks this when a player offers blocks and when the trade completes
    @Override
    public double getBalance(@NotNull UUID player) {
        if (instance == null) return 0;
        try {
            return Math.max(0, ((Number) getRemainingClaimBlocks.invoke(playerData(player))).intValue());
        } catch (Exception ex) {
            ex.printStackTrace();
            return 0;
        }
    }

    @Override
    public CompletableFuture<Boolean> giveBalance(@NotNull UUID player, double amount) {
        return CompletableFuture.completedFuture(adjust(player, (int) Math.round(amount)));
    }

    @Override
    public CompletableFuture<Boolean> takeBalance(@NotNull UUID player, double amount) {
        return CompletableFuture.completedFuture(adjust(player, -(int) Math.round(amount)));
    }

    private boolean adjust(@NotNull UUID player, int change) {
        if (instance == null) return false;
        if (change == 0) return true;
        try {
            Object data = playerData(player);
            if (change < 0 && ((Number) getRemainingClaimBlocks.invoke(data)).intValue() < -change) return false;

            int bonus = ((Number) getBonusClaimBlocks.invoke(data)).intValue();
            setBonusClaimBlocks.invoke(data, bonus + change);
            savePlayerData.invoke(dataStore(), player, data);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
