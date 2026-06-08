package com.mooncore.modules.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressTest {

    @Test
    void addCapsAndSignalsCompletion() {
        QuestProgress qp = QuestProgress.fresh();
        assertFalse(qp.add(3, 5));   // 3/5
        assertEquals(3, qp.progress());
        assertTrue(qp.add(10, 5));    // plafonné à 5 → étape complète
        assertEquals(5, qp.progress());
    }

    @Test
    void advanceResetsProgress() {
        QuestProgress qp = QuestProgress.fresh();
        qp.add(5, 5);
        qp.advance();
        assertEquals(1, qp.step());
        assertEquals(0, qp.progress());
        assertFalse(qp.completed());
    }

    @Test
    void completeSetsFlag() {
        QuestProgress qp = QuestProgress.fresh();
        qp.complete();
        assertTrue(qp.completed());
    }
}
