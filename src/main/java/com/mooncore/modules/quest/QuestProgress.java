package com.mooncore.modules.quest;

/** Avancement d'un joueur sur une quête (étape courante + progression). Pur, testable. */
public final class QuestProgress {

    private int step;
    private int progress;
    private boolean completed;
    private volatile boolean dirty;

    public QuestProgress(int step, int progress, boolean completed) {
        this.step = step;
        this.progress = progress;
        this.completed = completed;
    }

    public static QuestProgress fresh() {
        return new QuestProgress(0, 0, false);
    }

    /** Ajoute de la progression (plafonnée à la cible). Retourne true si l'étape est complète. */
    public boolean add(int amount, int target) {
        progress = Math.min(target, progress + amount);
        dirty = true;
        return progress >= target;
    }

    /** Passe à l'étape suivante (remet la progression à zéro). */
    public void advance() {
        step++;
        progress = 0;
        dirty = true;
    }

    public void complete() {
        completed = true;
        dirty = true;
    }

    public int step() { return step; }
    public int progress() { return progress; }
    public boolean completed() { return completed; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }
}
