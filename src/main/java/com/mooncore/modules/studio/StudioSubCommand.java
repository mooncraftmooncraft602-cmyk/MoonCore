package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.ChatInput;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Ouvre le studio admin unifié de création/édition de contenu. */
public final class StudioSubCommand implements SubCommand {

    private final ChatInput chat;

    public StudioSubCommand(ChatInput chat) {
        this.chat = chat;
    }

    @Override public String name() { return "studio"; }
    @Override public List<String> aliases() { return List.of("creator", "createhub", "atelier"); }
    @Override public String permission() { return "mooncore.admin.studio"; }
    @Override public String description() { return "Studio de création admin"; }
    @Override public String category() { return "admin"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        StudioHubMenu.open(plugin, chat, (Player) sender);
    }
}
