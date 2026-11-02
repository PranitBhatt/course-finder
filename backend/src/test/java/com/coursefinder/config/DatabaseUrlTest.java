package com.coursefinder.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlTest {

    @Test
    void parsesNeonStyleUrlAndForcesSsl() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgresql://neondb_owner:s3cr3t@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require");

        assertThat(url.jdbcUrl()).isEqualTo(
                "jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require");
        assertThat(url.username()).isEqualTo("neondb_owner");
        assertThat(url.password()).isEqualTo("s3cr3t");
    }

    @Test
    void parsesHerokuStyleUrlWithPort() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://user:pass@db.example.com:6543/mydb");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:6543/mydb?sslmode=require");
    }

    @Test
    void decodesUrlEncodedCredentials() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://my%40user:p%40ss%3Aword@host/db");

        assertThat(url.username()).isEqualTo("my@user");
        assertThat(url.password()).isEqualTo("p@ss:word");
    }

    @Test
    void toStringHidesPassword() {
        assertThat(DatabaseUrl.parse("postgresql://user:topsecret@host/db").toString())
                .doesNotContain("topsecret")
                .contains("****");
    }

    @Test
    void rejectsInvalidUrlsWithoutLeakingThem() {
        assertThatThrownBy(() -> DatabaseUrl.parse("mysql://user:secret@host/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret");
        assertThatThrownBy(() -> DatabaseUrl.parse("postgresql://host/db"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DatabaseUrl.parse("postgresql://user:pw@host/"))
                .hasMessageContaining("database name");
        assertThatThrownBy(() -> DatabaseUrl.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
