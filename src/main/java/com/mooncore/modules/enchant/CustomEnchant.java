package com.mooncore.modules.enchant;

import com.mooncore.api.enchant.EnchantTarget;
import com.mooncore.modules.enchant.effect.DefenseEffect;
import com.mooncore.modules.enchant.effect.EquipEffect;
import com.mooncore.modules.enchant.effect.MeleeHitEffect;
import com.mooncore.modules.enchant.effect.MiningEffect;

/**
 * Un enchantement custom : métadonnées + effets optionnels (mêlée, défense, minage, passif).
 * Construit via {@link Builder} dans {@link EnchantRegistry}.
 */
public final class CustomEnchant {

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final EnchantTarget target;
    private final String description;

    private final MeleeHitEffect melee;
    private final DefenseEffect defense;
    private final MiningEffect mining;
    private final EquipEffect equip;

    private CustomEnchant(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.maxLevel = b.maxLevel;
        this.target = b.target;
        this.description = b.description;
        this.melee = b.melee;
        this.defense = b.defense;
        this.mining = b.mining;
        this.equip = b.equip;
    }

    public static Builder builder(String id, String displayName, EnchantTarget target) {
        return new Builder(id, displayName, target);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int maxLevel() { return maxLevel; }
    public EnchantTarget target() { return target; }
    public String description() { return description; }

    public MeleeHitEffect melee() { return melee; }
    public DefenseEffect defense() { return defense; }
    public MiningEffect mining() { return mining; }
    public EquipEffect equip() { return equip; }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private final EnchantTarget target;
        private int maxLevel = 3;
        private String description = "";
        private MeleeHitEffect melee;
        private DefenseEffect defense;
        private MiningEffect mining;
        private EquipEffect equip;

        private Builder(String id, String displayName, EnchantTarget target) {
            this.id = id;
            this.displayName = displayName;
            this.target = target;
        }

        public Builder maxLevel(int v) { this.maxLevel = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder melee(MeleeHitEffect v) { this.melee = v; return this; }
        public Builder defense(DefenseEffect v) { this.defense = v; return this; }
        public Builder mining(MiningEffect v) { this.mining = v; return this; }
        public Builder equip(EquipEffect v) { this.equip = v; return this; }

        public CustomEnchant build() { return new CustomEnchant(this); }
    }
}
