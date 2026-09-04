/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.sql.common;

import java.util.Map;

import io.helidon.common.Errors.ErrorMessagesException;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests shared database connection configuration validation.
 */
class ConnectionConfigTest {

    /**
     * Verifies that the connection configuration decorator validates its SPI target.
     */
    @Test
    void validatesDecoratorTarget() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new ConnectionConfigSupport.Decorator().decorate(null));

        assertThat(failure.getMessage(), is("The connection configuration builder must not be null."));
    }

    /**
     * Verifies the generated required-property validator retains ownership of a missing URL,
     * including when another configured connection value is invalid.
     */
    @Test
    void preservesGeneratedMissingUrlValidation() {
        ErrorMessagesException failure = assertThrows(
                ErrorMessagesException.class,
                () -> ConnectionConfig.builder()
                        .jdbcDriverClassName("")
                        .buildPrototype());

        assertThat(failure.getMessage(), containsString("Property \"url\" must not be null, but not set"));
    }

    /**
     * Verifies builder and configuration-backed construction reject an explicitly empty URL
     * with the same safe diagnostic.
     */
    @Test
    void rejectsEmptyUrl() {
        DataException builderFailure = assertThrows(
                DataException.class,
                () -> ConnectionConfig.builder().url("").buildPrototype());
        DataException configuredFailure = assertThrows(
                DataException.class,
                () -> ConnectionConfig.create(config(Map.of("url", ""))));

        assertAll(
                () -> assertThat(builderFailure.getMessage(), is("The database connection URL must not be empty.")),
                () -> assertThat(configuredFailure.getMessage(), is("The database connection URL must not be empty.")));
    }

    /**
     * Verifies builder and configuration-backed construction reject an explicitly empty driver class name
     * with the same safe diagnostic.
     */
    @Test
    void rejectsEmptyDriverClassName() {
        DataException builderFailure = assertThrows(
                DataException.class,
                () -> ConnectionConfig.builder()
                        .url("jdbc:test")
                        .jdbcDriverClassName("")
                        .buildPrototype());
        DataException configuredFailure = assertThrows(
                DataException.class,
                () -> ConnectionConfig.create(config(Map.of(
                        "url", "jdbc:test",
                        "jdbc-driver-class-name", ""))));

        assertAll(
                () -> assertThat(builderFailure.getMessage(), is("The JDBC driver class name must not be empty.")),
                () -> assertThat(configuredFailure.getMessage(), is("The JDBC driver class name must not be empty.")));
    }

    /**
     * Verifies optional driver discovery, explicit driver selection, and empty credentials remain valid
     * through builder and configuration-backed construction.
     */
    @Test
    void acceptsSupportedOptionalAndCredentialValues() {
        ConnectionConfig builderWithoutDriver = ConnectionConfig.builder()
                .url("jdbc:test")
                .username("")
                .password("")
                .buildPrototype();
        ConnectionConfig builderWithDriver = ConnectionConfig.builder()
                .url("jdbc:test")
                .jdbcDriverClassName("example.Driver")
                .buildPrototype();
        ConnectionConfig configuredWithoutDriver = ConnectionConfig.create(config(Map.of(
                "url", "jdbc:test",
                "username", "",
                "password", "")));
        ConnectionConfig configuredWithDriver = ConnectionConfig.create(config(Map.of(
                "url", "jdbc:test",
                "jdbc-driver-class-name", "example.Driver")));

        assertAll(
                () -> assertThat(builderWithoutDriver.jdbcDriverClassName().isEmpty(), is(true)),
                () -> assertThat(builderWithoutDriver.username().orElseThrow(), is("")),
                () -> assertThat(builderWithoutDriver.password().orElseThrow().length, is(0)),
                () -> assertThat(builderWithDriver.jdbcDriverClassName().orElseThrow(), is("example.Driver")),
                () -> assertThat(configuredWithoutDriver.jdbcDriverClassName().isEmpty(), is(true)),
                () -> assertThat(configuredWithoutDriver.username().orElseThrow(), is("")),
                () -> assertThat(configuredWithoutDriver.password().orElseThrow().length, is(0)),
                () -> assertThat(configuredWithDriver.jdbcDriverClassName().orElseThrow(), is("example.Driver")));
    }

    /**
     * Verifies the empty-value rule does not trim or broaden validation to whitespace-only values
     * through either supported construction path.
     */
    @Test
    void preservesWhitespaceOnlyValues() {
        ConnectionConfig builderConfig = ConnectionConfig.builder()
                .url(" ")
                .jdbcDriverClassName("\t")
                .buildPrototype();
        ConnectionConfig configuredConfig = ConnectionConfig.create(config(Map.of(
                "url", " ",
                "jdbc-driver-class-name", "\t")));

        assertAll(
                () -> assertThat(builderConfig.url(), is(" ")),
                () -> assertThat(builderConfig.jdbcDriverClassName().orElseThrow(), is("\t")),
                () -> assertThat(configuredConfig.url(), is(" ")),
                () -> assertThat(configuredConfig.jdbcDriverClassName().orElseThrow(), is("\t")));
    }

    private static Config config(Map<String, String> values) {
        return Config.just(ConfigSources.create(values));
    }
}
