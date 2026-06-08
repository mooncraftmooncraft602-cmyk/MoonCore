package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.quest.Quest;
import com.mooncore.modules.quest.QuestManagerModule;
import com.mooncore.modules.quest.QuestProgress;
import com.mooncore.modules.quest.QuestStep;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /moon quest} — affiche tes quêtes et ta progression. */
public final class QuestSubCommand implements SubCommand {

    private final QuestManagerModule module;

    public QuestSubCommand(QuestManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "quest"; }
    @Override public List<String> aliases() { return List.of("quests", "quete"); }
    @Override public String permission() { return "mooncore.missions.view"; }
    @Override public String description() { return "Affiche tes quêtes"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;
        sender.sendMessage(cm.message("quest-header"));
        if (module.quests().isEmpty()) {
            sender.sendMessage(cm.message("quest-empty"));
            return;
        }
        for (Quest quest : module.quests()) {
            QuestProgress qp = module.progressOf(p.getUniqueId(), quest.id());
            String state;
            if (qp != null && qp.completed()) {
                state = "<green>terminée</green>";
            } else {
                int stepIdx = qp != null ? qp.step() : 0;
                if (stepIdx >= quest.steps().size()) {
                    state = "<green>terminée</green>";
                } else {
                    QuestStep step = quest.steps().get(stepIdx);
                    int prog = qp != null ? qp.progress() : 0;
                    state = "<gray>étape " + (stepIdx + 1) + "/" + quest.steps().size()
                            + " : " + step.description() + " (" + prog + "/" + step.target() + ")</gray>";
                }
            }
            sender.sendMessage(cm.message("quest-entry", "quest", quest.displayName(), "state", state));
        }
    }
}
