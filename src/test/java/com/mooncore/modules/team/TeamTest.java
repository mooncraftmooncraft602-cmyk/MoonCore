package com.mooncore.modules.team;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTest {

    @Test
    void ownerIsMemberFromCreation() {
        UUID owner = UUID.randomUUID();
        Team t = new Team("alpha", "Alpha", owner, 0, "s1");
        assertTrue(t.isOwner(owner));
        assertTrue(t.isMember(owner));
        assertEquals(1, t.size());
        assertEquals(Team.ROLE_OWNER, t.role(owner));
    }

    @Test
    void addAndRemoveMember() {
        UUID owner = UUID.randomUUID();
        UUID m = UUID.randomUUID();
        Team t = new Team("alpha", "Alpha", owner, 0, "s1");
        t.addMember(m);
        assertEquals(2, t.size());
        assertEquals(Team.ROLE_MEMBER, t.role(m));
        t.removeMember(m);
        assertFalse(t.isMember(m));
        assertEquals(1, t.size());
    }

    @Test
    void nameValidation() {
        assertTrue(TeamNames.isValid("Alpha_1"));
        assertFalse(TeamNames.isValid("ab"));            // trop court
        assertFalse(TeamNames.isValid("nom avec espace"));
        assertFalse(TeamNames.isValid("trop_long_nom_dequipe_xxx"));
        assertEquals("alpha", TeamNames.toId("Alpha"));
    }
}
