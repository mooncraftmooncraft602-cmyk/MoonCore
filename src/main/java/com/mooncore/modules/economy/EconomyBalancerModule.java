package com.mooncore.modules.economy;

import com.mooncore.api.economy.AbnormalGainEvent;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.command.sub.EconomySubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EconomyBalancer : lutte contre l'inflation au-dessus de Vault (l'économie existante reste
 * la source de vérité). Apporte taxe de transaction, frais (téléport/réparation), taxe
 * progressive périodique sur la richesse (money sink), journal d'audit et détection de gains
 * anormaux. Se dégrade proprement si Vault est absent.
 */
@ModuleInfo(id = "economy-balancer", name = "EconomyBalancer", softDepends = {"statistics", "anti-afk"})
public final class EconomyBalancerModule extends AbstractModule implements EconomyService {

    private Economy vault;
    private EconomyLedger ledger;
    private ProgressiveTax wealthTax;
    private AbnormalGainDetector detector;

    private double transactionTaxRate;
    private double teleportFee;
    private double repairFee;
    private List<String> feeCauses;
    private long wealthTaxIntervalSeconds;
    private boolean alertEnabled;

    private BukkitTask wealthTaxTask;
    private BukkitTask cleanupTask;
    private volatile long lastAlert;

    @Override
    protected void onEnable() throws Exception {
        this.ledger = new EconomyLedger(data().database());
        data().applyMigrations(EconomyLedger.migrations());

        loadConfig();
        hookVault();

        services().register(EconomyService.class, this);
        registerListener(new FeeListener(plugin(), this));
        plugin().rootCommand().register(new EconomySubCommand(this));

        if (vault != null && wealthTaxIntervalSeconds > 0) {
            long ticks = wealthTaxIntervalSeconds * 20L;
            wealthTaxTask = schedulers().syncTimer(this::applyWealthTax, ticks, ticks);
        }
        cleanupTask = schedulers().asyncTimer(
                () -> detector.cleanup(System.currentTimeMillis()), 6000L, 6000L);
    }

    @Override
    protected void onDisable() {
        if (wealthTaxTask != null) wealthTaxTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        services().unregister(EconomyService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration c = moduleConfig();
        this.transactionTaxRate = clampRate(c.getDouble("transaction-tax-rate", 0.05));
        this.teleportFee = Math.max(0, c.getDouble("fees.teleport", 0));
        this.repairFee = Math.max(0, c.getDouble("fees.repair", 0));
        this.feeCauses = c.getStringList("fees.teleport-causes");
        if (feeCauses.isEmpty()) feeCauses = List.of("COMMAND", "PLUGIN");

        this.wealthTaxIntervalSeconds = c.getLong("wealth-tax.interval-seconds", 0);
        this.wealthTax = new ProgressiveTax(parseBrackets(c.getList("wealth-tax.brackets")));

        long window = c.getLong("abnormal-gain.window-seconds", 300) * 1000L;
        double threshold = c.getDouble("abnormal-gain.threshold", 0);
        this.detector = new AbnormalGainDetector(window, threshold);
        this.alertEnabled = c.getBoolean("abnormal-gain.alert", true);
    }

    private List<ProgressiveTax.Bracket> parseBrackets(List<?> raw) {
        List<ProgressiveTax.Bracket> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof java.util.Map<?, ?> map) {
                double from = toDouble(map.get("from"), 0);
                double rate = clampRate(toDouble(map.get("rate"), 0));
                out.add(new ProgressiveTax.Bracket(from, rate));
            }
        }
        return out;
    }

    private static double toDouble(Object o, double def) {
        return (o instanceof Number n) ? n.doubleValue() : def;
    }

    private static double clampRate(double r) {
        return Math.max(0, Math.min(1, r));
    }

    private void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            log().warn("Vault introuvable : EconomyBalancer tourne en mode dégradé (taxes/frais inactifs).");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            log().warn("Aucun fournisseur d'économie Vault enregistré : mode dégradé.");
            return;
        }
        this.vault = rsp.getProvider();
        log().info("EconomyBalancer relié à Vault (" + vault.getName() + ").");
    }

    private void applyWealthTax() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("mooncore.bypass.economy.tax")) continue;
            double bal = vault.getBalance(p);
            double tax = wealthTax.computeTax(bal);
            if (tax <= 0) continue;
            vault.withdrawPlayer(p, tax);
            ledger.log(p.getUniqueId(), -tax, EconomyLedger.Type.SINK, "wealth-tax");
        }
    }

    // ---- EconomyService ----

    @Override
    public boolean isAvailable() { return vault != null; }

    @Override
    public double balance(UUID player) {
        return vault == null ? 0 : vault.getBalance(Bukkit.getOfflinePlayer(player));
    }

    @Override
    public boolean has(UUID player, double amount) {
        return vault != null && vault.has(Bukkit.getOfflinePlayer(player), amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount, String reason) {
        if (vault == null || amount <= 0) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        if (!vault.has(op, amount)) return false;
        if (!vault.withdrawPlayer(op, amount).transactionSuccess()) return false;
        ledger.log(player, -amount, EconomyLedger.Type.SINK, reason);
        return true;
    }

    @Override
    public void deposit(UUID player, double amount, String reason) {
        if (vault == null || amount <= 0) return;
        vault.depositPlayer(Bukkit.getOfflinePlayer(player), amount);
        ledger.log(player, amount, EconomyLedger.Type.GAIN, reason);
    }

    @Override
    public double depositWithTax(UUID player, double gross, String reason) {
        if (vault == null || gross <= 0) return 0;
        double tax = transactionTax(gross);
        double net = gross - tax;
        OfflinePlayer op = Bukkit.getOfflinePlayer(player);
        vault.depositPlayer(op, net);
        ledger.log(player, net, EconomyLedger.Type.GAIN, reason);
        if (tax > 0) ledger.log(player, -tax, EconomyLedger.Type.TAX, reason + ":tax");

        if (detector.record(player, gross, System.currentTimeMillis())) {
            double total = detector.windowTotal(player, System.currentTimeMillis());
            eventBus().post(new AbnormalGainEvent(player, total, reason));
            alertAbnormal(op.getName() == null ? player.toString() : op.getName(), total);
        }
        return net;
    }

    @Override
    public double transactionTax(double gross) {
        return gross <= 0 ? 0 : gross * transactionTaxRate;
    }

    private void alertAbnormal(String name, double total) {
        if (!alertEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastAlert < 30_000) return;
        lastAlert = now;
        var msg = plugin().configManager().message("economy-alert-abnormal",
                "player", name, "total", String.format(java.util.Locale.ROOT, "%.0f", total));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("mooncore.admin.economy")) p.sendMessage(msg);
        }
    }

    // ---- Accès commande ----

    public double teleportFee() { return teleportFee; }
    public double repairFee() { return repairFee; }
    public List<String> feeCauses() { return feeCauses; }
    public double transactionTaxRate() { return transactionTaxRate; }
    public ProgressiveTax wealthTax() { return wealthTax; }
    public EconomyLedger ledger() { return ledger; }
}
