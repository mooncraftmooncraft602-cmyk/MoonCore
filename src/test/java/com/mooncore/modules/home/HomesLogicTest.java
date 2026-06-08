package com.mooncore.modules.home;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomesLogicTest {

    @Test
    void nameValidation() {
        assertTrue(HomesLogic.isValidName("base"));
        assertTrue(HomesLogic.isValidName("Home_2"));
        assertFalse(HomesLogic.isValidName(""));
        assertFalse(HomesLogic.isValidName("nom avec espace"));
        assertFalse(HomesLogic.isValidName("trop_long_nom_de_home_xxx"));
        assertEquals("base", HomesLogic.normalize("BASE"));
    }

    @Test
    void limitEnforced() {
        assertTrue(HomesLogic.canCreate(0, 3));
        assertTrue(HomesLogic.canCreate(2, 3));
        assertFalse(HomesLogic.canCreate(3, 3));
    }

    @Test
    void unlimitedWhenMaxZeroOrLess() {
        assertTrue(HomesLogic.canCreate(999, 0));
        assertTrue(HomesLogic.canCreate(999, -1));
    }
}
