package org.example.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUsersTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("usertest.user.leser.username");
        System.clearProperty("usertest.user.leser.password");
        System.clearProperty("env.usersenv.usertest.user.leser.password");
        System.clearProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY);
    }

    @Test
    void forRole_ConfiguredGlobally_ReturnsCredentials() {
        System.setProperty("usertest.user.leser.username", "lea");
        System.setProperty("usertest.user.leser.password", "geheim");

        TestUsers.Credentials credentials = TestUsers.forRole("usertest", "leser");

        assertEquals("lea", credentials.username());
        assertEquals("geheim", credentials.password());
    }

    @Test
    void forRole_ActiveEnvironment_OverridesOnlyConfiguredParts() {
        System.setProperty("usertest.user.leser.username", "lea");
        System.setProperty("usertest.user.leser.password", "geheim");
        System.setProperty("env.usersenv.usertest.user.leser.password", "feature-geheim");
        System.setProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, "usersenv");

        TestUsers.Credentials credentials = TestUsers.forRole("usertest", "leser");

        assertEquals("lea", credentials.username());
        assertEquals("feature-geheim", credentials.password());
    }

    @Test
    void forRole_PasswordMissing_ThrowsWithKeyHint() {
        System.setProperty("usertest.user.leser.username", "lea");
        System.setProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, "usersenv");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TestUsers.forRole("usertest", "leser"));

        assertTrue(e.getMessage().contains("usertest.user.leser.username/password"), e.getMessage());
        assertTrue(e.getMessage().contains("env.usersenv.usertest.user.leser.username/password"), e.getMessage());
    }

    @Test
    void forRole_InvalidRoleName_Throws() {
        assertThrows(IllegalArgumentException.class, () -> TestUsers.forRole("usertest", "admin.password"));
        assertThrows(IllegalArgumentException.class, () -> TestUsers.forRole("usertest", null));
    }

    @Test
    void credentials_ToString_MasksPassword() {
        assertFalse(new TestUsers.Credentials("lea", "geheim").toString().contains("geheim"));
    }
}
