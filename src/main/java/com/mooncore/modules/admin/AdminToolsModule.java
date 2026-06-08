package com.mooncore.modules.admin;

import com.mooncore.command.sub.AdminSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.studio.StudioListener;
import com.mooncore.modules.studio.StudioSubCommand;
import com.mooncore.util.ChatInput;

/**
 * AdminTools : outils transverses d'administration in-game (inspection joueur unifiée,
 * debug, infos). S'appuie sur les services exposés par les autres modules.
 */
@ModuleInfo(id = "admin-tools", name = "AdminTools",
        softDepends = {"progression", "statistics", "anti-afk", "economy-balancer",
                "anti-farm", "boss", "event"})
public final class AdminToolsModule extends AbstractModule {

    private ChatInput studioChat;

    @Override
    protected void onEnable() {
        plugin().rootCommand().register(new AdminSubCommand());
        this.studioChat = new ChatInput(plugin());
        registerListener(studioChat);
        registerListener(new StudioListener());
        plugin().rootCommand().register(new StudioSubCommand(studioChat));
    }

    @Override
    protected void onDisable() {
        // Rien à libérer.
    }
}
