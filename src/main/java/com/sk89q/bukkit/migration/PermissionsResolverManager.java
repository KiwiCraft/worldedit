// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.bukkit.migration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

public class PermissionsResolverManager implements PermissionsResolver {
    private static final String CONFIG_HEADER = "#\r\n" +
            "# WEPIF Configuration File\r\n" +
            "#\r\n" +
            "# This file handles permissions configuration for every plugin using WEPIF\r\n" +
            "#\r\n" +
            "# About editing this file:\r\n" +
            "# - DO NOT USE TABS. You MUST use spaces or Bukkit will complain. If\r\n" +
            "#   you use an editor like Notepad++ (recommended for Windows users), you\r\n" +
            "#   must configure it to \"replace tabs with spaces.\" In Notepad++, this can\r\n" +
            "#   be changed in Settings > Preferences > Language Menu.\r\n" +
            "# - Don't get rid of the indents. They are indented so some entries are\r\n" +
            "#   in categories (like \"enforce-single-session\" is in the \"protection\"\r\n" +
            "#   category.\r\n" +
            "# - If you want to check the format of this file before putting it\r\n" +
            "#   into WEPIF, paste it into http://yaml-online-parser.appspot.com/\r\n" +
            "#   and see if it gives \"ERROR:\".\r\n" +
            "# - Lines starting with # are comments and so they are ignored.\r\n" +
            "#\r\n" +
            "# About Configuration Permissions\r\n" +
            "# - See http://wiki.sk89q.com/wiki/WorldEdit/Permissions/Bukkit\r\n" +
            "# - Now with multiworld support (see example)\r\n" +
            "\r\n";

    private Server server;
    private PermissionsResolver permissionResolver;
    private PermissionsResolverServerListener listener;
    private YAMLProcessor config;
    private String name;
    private Logger logger;
    private List<Class<? extends PermissionsResolver>> enabledResolvers = new ArrayList<Class<? extends PermissionsResolver>>();

    @SuppressWarnings("unchecked")
    protected Class<? extends PermissionsResolver>[] availableResolvers = new Class[] {
            PluginPermissionsResolver.class,
            PermissionsExResolver.class,
            NijiPermissionsResolver.class,
            DinnerPermsResolver.class,
            FlatFilePermissionsResolver.class
    };

    @Deprecated
    public PermissionsResolverManager(org.bukkit.util.config.Configuration config, Server server, String name, Logger logger) {
        this.server = server;
        this.name = name;
        this.logger = logger;
        loadConfig(new File("wepif.yml")); // TODO: config migration, maybe
        findResolver();
    }

    public PermissionsResolverManager(Plugin plugin, String name, Logger logger) {
        this.server = plugin.getServer();
        this.name = name;
        this.logger = logger;
        this.listener = new PermissionsResolverServerListener(this, plugin);

        loadConfig(new File("wepif.yml"));
        findResolver();
    }

    public void findResolver() {
        for (Class<? extends PermissionsResolver> resolverClass : enabledResolvers) {
            try {
                Method factoryMethod = resolverClass.getMethod("factory", Server.class, YAMLProcessor.class);

                this.permissionResolver = (PermissionsResolver) factoryMethod.invoke(null, this.server, this.config);

                if (this.permissionResolver != null) {
                    break;
                }
            } catch (Throwable e) {
                logger.warning("Error in factory method for " + resolverClass.getSimpleName() + ": " + e);
                e.printStackTrace();
                continue;
            }
        }
        if (permissionResolver == null) {
            permissionResolver = new ConfigurationPermissionsResolver(config);
        }
        permissionResolver.load();
        logger.info(name + ": " + permissionResolver.getDetectionMessage());
    }

    public void setPluginPermissionsResolver(Plugin plugin) {
        if (!(plugin instanceof PermissionsProvider)) {
            return;
        }

        permissionResolver = new PluginPermissionsResolver((PermissionsProvider) plugin, plugin);
        logger.info(name + ": " + permissionResolver.getDetectionMessage());
    }

    public void load() {
        permissionResolver.load();
    }

    public boolean hasPermission(String name, String permission) {
        return permissionResolver.hasPermission(name, permission);
    }

    public boolean hasPermission(String worldName, String name, String permission) {
        return permissionResolver.hasPermission(worldName, name, permission);
    }

    public boolean inGroup(String player, String group) {
        return permissionResolver.inGroup(player, group);
    }

    public String[] getGroups(String player) {
        return permissionResolver.getGroups(player);
    }

    private boolean loadConfig(File file) {
        boolean isUpdated = false;
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = new YAMLProcessor(file, false, YAMLFormat.EXTENDED);
        try {
            config.load();
        } catch (IOException e) {
            logger.severe("Error loading WEPIF Config: " + e);
            e.printStackTrace();
        }
        List<String> keys = config.getKeys(null);
        config.setHeader(CONFIG_HEADER);

        if (!keys.contains("ignore-nijiperms-bridges")) {
            config.setProperty("ignore-nijiperms-bridges", true);
            isUpdated = true;
        }

        if (!keys.contains("resolvers")) {
            //List<String> resolverKeys = config.getKeys("resolvers");
            List<String> resolvers = new ArrayList<String>();
            for (Class<?> clazz : availableResolvers) {
                resolvers.add(clazz.getSimpleName());
            }
            enabledResolvers.addAll(Arrays.asList(availableResolvers));
            config.setProperty("resolvers.enabled", resolvers);
            isUpdated = true;
        } else {
            List<String> disabledResolvers = config.getStringList("resolvers.disabled", new ArrayList<String>());
            List<String> stagedEnabled = config.getStringList("resolvers.enabled", null);
            for (Iterator<String> i = stagedEnabled.iterator(); i.hasNext();) {
                String nextName = i.next();
                Class<?> next = null;
                try {
                    next = Class.forName(getClass().getPackage().getName() + "." + nextName);
                } catch (ClassNotFoundException e) {}
                
                if (next == null || !PermissionsResolver.class.isAssignableFrom(next)) {
                    logger.warning("WEPIF: Invalid or unknown class found in enabled resolvers: "
                            + nextName + ". Moving to disabled resolvers list.");
                    i.remove();
                    disabledResolvers.add(nextName);
                    isUpdated = true;
                    continue;
                }
                enabledResolvers.add(next.asSubclass(PermissionsResolver.class));
            }

            for (Class<?> clazz : availableResolvers) {
                if (!stagedEnabled.contains(clazz.getSimpleName()) &&
                        !disabledResolvers.contains(clazz.getSimpleName())) {
                    disabledResolvers.add(clazz.getSimpleName());
                    logger.info("New permissions resolver: "
                            + clazz.getSimpleName() + " detected. " +
                            "Added to disabled resolvers list.");
                    isUpdated = true;
                }
            }
            config.setProperty("resolvers.disabled", disabledResolvers);
            config.setProperty("resolvers.enabled", stagedEnabled);
        }

        if (keys.contains("dinner-perms") || keys.contains("dinnerperms")) {
            config.setProperty("dinner-perms", null);
            config.setProperty("dinnerperms", null);
            isUpdated = true;
        }
        if (!keys.contains("permissions")) {
            ConfigurationPermissionsResolver.generateDefaultPerms(
                    config.addNode("permissions"));
            isUpdated = true;
        }
        if (isUpdated) {
            logger.info("WEPIF: Updated config file");
            config.save();
        }
        return isUpdated;
    }

    boolean hasServerListener() {
        return listener != null;
    }

    void setServerListener(PermissionsResolverServerListener listener) {
        this.listener = listener;
    }

    public static class MissingPluginException extends Exception {
        private static final long serialVersionUID = 7044832912491608706L;
    }

    public String getDetectionMessage() {
        return "Using WEPIF for permissions";
    }

}
