package org.example.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestUsersTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("testusers.user.admin.username");
        System.clearProperty("testusers.user.admin.password");
        System.clearProperty("env.staging.testusers.user.admin.password");
        System.clearProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY);
    }

    @Test
    void forRole_ReadsUsernameAndPasswordFromConfig() {
        System.setProperty("testusers.user.admin.username", "alice");
        System.setProperty("testusers.user.admin.password", "secret");

        TestUsers.Credentials credentials = TestUsers.forRole("testusers", "admin");

        assertEquals("alice", credentials.username());
        assertEquals("secret", credentials.password());
    }

    @Test
    void forRole_ActiveEnvironment_UsesScopedValueAndGlobalFallback() {
        System.setProperty("testusers.user.admin.username", "alice");
        System.setProperty("testusers.user.admin.password", "global-secret");
        System.setProperty("env.staging.testusers.user.admin.password", "staging-secret");
        System.setProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, "staging");

        TestUsers.Credentials credentials = TestUsers.forRole("testusers", "admin");

        assertEquals("alice", credentials.username());
        assertEquals("staging-secret", credentials.password());
    }

    @Test
    void forRole_MissingPassword_ThrowsWithKeyAndEnvVarName() {
        System.setProperty("testusers.user.admin.username", "alice");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TestUsers.forRole("testusers", "admin"));

        assertTrue(e.getMessage().contains("testusers.user.admin.password"), e.getMessage());
        assertTrue(e.getMessage().contains("TESTUSERS_USER_ADMIN_PASSWORD"), e.getMessage());
    }

    @Test
    void forRole_MissingValueWithActiveEnvironment_NamesScopedEnvVar() {
        System.setProperty(ConfigReader.ACTIVE_ENVIRONMENT_PROPERTY, "staging");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TestUsers.forRole("testusers", "admin"));

        assertTrue(e.getMessage().contains("ENV_STAGING_TESTUSERS_USER_ADMIN_USERNAME"), e.getMessage());
    }

    @Test
    void credentials_ToStringMasksPassword() {
        String text = new TestUsers.Credentials("alice", "secret").toString();

        assertTrue(text.contains("alice"));
        assertFalse(text.contains("secret"));
    }
}
