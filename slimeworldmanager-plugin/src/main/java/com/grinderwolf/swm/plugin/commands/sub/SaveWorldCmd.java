package com.grinderwolf.swm.plugin.commands.sub;


import com.grinderwolf.swm.api.exceptions.*;
import com.grinderwolf.swm.api.loaders.SlimeLoader;
import com.grinderwolf.swm.api.world.SlimeWorld;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.commands.CommandManager;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.config.WorldData;
import com.grinderwolf.swm.plugin.config.WorldsConfig;
import com.grinderwolf.swm.plugin.loaders.LoaderUtils;
import com.grinderwolf.swm.plugin.log.Logging;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Getter
public class SaveWorldCmd implements Subcommand {

    private final String usage = "save <world>>";
    private final String description = "Saves a world.";
    private final String permission = "swm.saveworld";

    @Override
    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);

            if (world == null) {
                sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.RED + "World " + args[0] + " is not loaded!");

                return true;
            }

            world.save();

            sender.sendMessage(Logging.COMMAND_PREFIX + ChatColor.GREEN + "World " + ChatColor.YELLOW + args[0] + ChatColor.GREEN + " saved correctly.");

            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            List<String> completes = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                completes.add(world.getName());
            }
            return completes;
        }

        return Collections.emptyList();
    }
}

