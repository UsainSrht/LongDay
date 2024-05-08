package me.usainsrht.longday;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;

public final class LongDay extends JavaPlugin {

    HashMap<World, Integer> taskIDs;

    @Override
    public void onEnable() {
        taskIDs = new HashMap<>();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            //start timers when server is fully loaded.
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) continue;
                if (!Boolean.parseBoolean(world.getGameRuleValue("doDaylightCycle"))) continue;
                int taskID = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                    int i = 0;
                    @Override
                    public void run() {
                        long time = world.getTime();
                        // 12000>time>0 = daytime  --- every 2nd tick
                        if (time > 0 && time < 12000 && i % 2 == 0) {
                            world.setTime(time-1);
                        } else if (i % 2 == 0) { //night --- every 2nd tick
                            world.setTime(time+1);
                        }
                        i++;
                        if (i > 2) i = 0;
                    }
                }, 1L, 1L).getTaskId();
                taskIDs.put(world, taskID);
            }
        }, 1L);
    }

    @Override
    public void onDisable() {
        taskIDs.values().forEach(Bukkit.getScheduler()::cancelTask);
    }


}
