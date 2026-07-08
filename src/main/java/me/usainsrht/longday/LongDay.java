package me.usainsrht.longday;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LongDay extends JavaPlugin {

    private static final int PHASE_TICKS = 12000;
    private static final double REAL_TICKS_PER_MINUTE = 60.0 * 20.0;

    private final Map<World, WorldSession> sessions = new HashMap<>();

    @Override
    public void onEnable() {
        new Metrics(this, 32418);
        saveDefaultConfig();

        Bukkit.getScheduler().runTaskLater(this, this::startAllWorlds, 1L);
    }

    @Override
    public void onDisable() {
        for (WorldSession session : sessions.values()) {
            Bukkit.getScheduler().cancelTask(session.taskId);
            if (session.previousDaylightCycle != null) {
                setDaylightCycle(session.world, session.previousDaylightCycle);
            }
        }
        sessions.clear();
    }

    private void startAllWorlds() {
        if (!getConfig().getBoolean("enabled", true)) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            startWorld(world);
        }
    }

    private void startWorld(World world) {
        if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
            return;
        }
        if (isBlacklisted(world.getName())) {
            return;
        }

        boolean rewindMode = getConfig().getBoolean("rewind-mode", false);
        if (rewindMode && !isDaylightCycleEnabled(world)) {
            return;
        }

        WorldSession session = new WorldSession(world);
        if (!rewindMode) {
            session.previousDaylightCycle = getDaylightCycle(world);
            setDaylightCycle(world, false);
        }

        double dayMinutes = getConfig().getDouble("daytime", 15.0);
        double nightMinutes = getConfig().getDouble("nighttime", 5.0);

        session.taskId = Bukkit.getScheduler().runTaskTimer(this, () -> tickWorld(session, rewindMode, dayMinutes, nightMinutes), 1L, 1L).getTaskId();
        sessions.put(world, session);
    }

    private void tickWorld(WorldSession session, boolean rewindMode, double dayMinutes, double nightMinutes) {
        World world = session.world;
        long time = world.getTime();
        double targetRate = phaseRate(isDaytime(time) ? dayMinutes : nightMinutes);

        if (rewindMode) {
            session.accumulator += targetRate - 1.0;
            applyFullTimeAdjustment(world, session);
        } else {
            session.accumulator += targetRate;
            applyForwardTimeAdvance(world, session);
        }
    }

    private static double phaseRate(double minutes) {
        if (minutes <= 0.0) {
            return PHASE_TICKS / (10.0 * REAL_TICKS_PER_MINUTE);
        }
        return PHASE_TICKS / (minutes * REAL_TICKS_PER_MINUTE);
    }

    private static boolean isDaytime(long time) {
        return time > 0 && time < PHASE_TICKS;
    }

    private static void applyFullTimeAdjustment(World world, WorldSession session) {
        while (session.accumulator <= -1.0) {
            session.accumulator += 1.0;
            world.setFullTime(world.getFullTime() - 1);
        }
        while (session.accumulator >= 1.0) {
            session.accumulator -= 1.0;
            world.setFullTime(world.getFullTime() + 1);
        }
    }

    private static void applyForwardTimeAdvance(World world, WorldSession session) {
        while (session.accumulator >= 1.0) {
            session.accumulator -= 1.0;
            long time = world.getTime();
            world.setTime(time >= 23999 ? 0 : time + 1);
        }
    }

    private boolean isBlacklisted(String worldName) {
        List<String> blacklisted = getConfig().getStringList("blacklisted-worlds");
        Set<String> names = new HashSet<>();
        for (String name : blacklisted) {
            names.add(name.toLowerCase());
        }
        return names.contains(worldName.toLowerCase());
    }

    private static boolean isDaylightCycleEnabled(World world) {
        Boolean value = getDaylightCycle(world);
        return value == null || value;
    }

    private static Boolean getDaylightCycle(World world) {
        String advanceTime = world.getGameRuleValue("advance_time");
        if (advanceTime != null) {
            return Boolean.parseBoolean(advanceTime);
        }
        String doDaylightCycle = world.getGameRuleValue("doDaylightCycle");
        if (doDaylightCycle != null) {
            return Boolean.parseBoolean(doDaylightCycle);
        }
        return true;
    }

    private static void setDaylightCycle(World world, boolean enabled) {
        String value = Boolean.toString(enabled);
        if (world.getGameRuleValue("advance_time") != null) {
            world.setGameRuleValue("advance_time", value);
        }
        world.setGameRuleValue("doDaylightCycle", value);
    }

    private static final class WorldSession {
        private final World world;
        private int taskId;
        private double accumulator;
        private Boolean previousDaylightCycle;

        private WorldSession(World world) {
            this.world = world;
        }
    }

}
