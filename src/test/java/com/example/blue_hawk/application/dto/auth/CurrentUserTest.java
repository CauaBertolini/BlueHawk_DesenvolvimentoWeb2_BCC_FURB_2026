package com.example.blue_hawk.application.dto.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE = UUID.randomUUID();

    @Test
    void selfIsOnlyTheSameUser() {
        CurrentUser me = new CurrentUser(ME, false);
        assertTrue(me.isSelf(ME));
        assertFalse(me.isSelf(SOMEONE));
        assertFalse(me.isSelfOrAdmin(SOMEONE));
    }

    @Test
    void adminManagesAnyoneButIsNotSelf() {
        CurrentUser admin = new CurrentUser(ME, true);
        assertTrue(admin.isSelfOrAdmin(SOMEONE));
        assertFalse(admin.isSelf(SOMEONE));
    }

    @Test
    void anonymousIsNeverSelf() {
        CurrentUser visitor = CurrentUser.anonymous();
        assertFalse(visitor.authenticated());
        assertFalse(visitor.isSelf(null));
        assertFalse(visitor.isSelfOrAdmin(SOMEONE));
    }
}
