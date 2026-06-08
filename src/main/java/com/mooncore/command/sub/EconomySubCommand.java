package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.economy.EconomyBalancerModule;
import com.mooncore.modules.economy.ProgressiveTax;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon economy <info|tax>} — diagnostic et simulation économique. */
public final class EconomySubCommand implements SubCommand {

    private final EconomyBalancerModule module;

    public EconomySubCommand(EconomyBalancerModule module) {
        this.module = module;
    }

    @Override public String name() { return "economy"; }
    @Override public List<String> aliases() { return List.of("eco"); }
    @Override public String permission() { return "mooncore.admin.economy"; }
    @Override public String description() { return "Diagnostic et simulation économique"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "info" -> {
                sender.sendMessage(cm.message("economy-info-header"));
                sender.sendMessage(cm.message("economy-info-vault",
                        "status", module.isAvailable() ? "connecté" : "indisponible"));
                sender.sendMessage(cm.message("economy-info-rates",
                        "tax", String.format(Locale.ROOT, "%.0f%%", module.transactionTaxRate() * 100),
                        "tp", format(module.teleportFee()),
                        "repair", format(module.repairFee())));
                StringBuilder brackets = new StringBuilder();
                for (ProgressiveTax.Bracket b : module.wealthTax().brackets()) {
                    if (brackets.length() > 0) brackets.append(", ");
                    brackets.append(format(b.from())).append("→")
                            .append(String.format(Locale.ROOT, "%.0f%%", b.rate() * 100));
                }
                sender.sendMessage(cm.message("economy-info-wealthtax", "brackets", brackets.toString()));
            }
            case "tax" -> {
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("economy-tax-usage"));
                    return;
                }
                double wealth;
                try {
                    wealth = Double.parseDouble(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(cm.prefixed("economy-tax-usage"));
                    return;
                }
                double tax = module.wealthTax().computeTax(wealth);
                sender.sendMessage(cm.message("economy-tax-result",
                        "wealth", format(wealth),
                        "tax", format(tax),
                        "rate", String.format(Locale.ROOT, "%.1f%%", module.wealthTax().effectiveRate(wealth) * 100)));
            }
            default -> sender.sendMessage(cm.prefixed("economy-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("info", "tax")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        return List.of();
    }

    private String format(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
