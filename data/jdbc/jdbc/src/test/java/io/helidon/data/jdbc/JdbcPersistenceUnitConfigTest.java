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
package io.helidon.data.jdbc;

import java.util.List;
import java.util.Map;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.data.DataException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcPersistenceUnitConfigTest {

    @Test
    void acceptsDirectConnectionWithoutCredentials() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "connection.url", "jdbc:mysql://localhost/test",
                "connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver")));

        JdbcPersistenceUnitConfig unit = JdbcPersistenceUnitConfig.create(config);

        assertThat(unit.connection().orElseThrow().username().isEmpty(), is(true));
        assertThat(unit.connection().orElseThrow().password().isEmpty(), is(true));
    }

    @Test
    void mapsConfiguredResourceAndAcceptsProgrammaticResource() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data-source", "source",
                "init-script.content-plain", "SELECT 1")));

        JdbcPersistenceUnitConfig configured = JdbcPersistenceUnitConfig.create(config);
        JdbcPersistenceUnitConfig programmatic = JdbcPersistenceUnitConfig.builder()
                .dataSource("source")
                .initScript(Resource.create("programmatic script", "SELECT 1"))
                .buildPrototype();

        assertThat(configured.initScript().orElseThrow().sourceType(), is(Resource.Source.CONTENT));
        assertThat(configured.initScript().orElseThrow().string(), is("SELECT 1"));
        assertThat(programmatic.initScript().orElseThrow().sourceType(), is(Resource.Source.CONTENT));
        assertThat(programmatic.initScript().orElseThrow().string(), is("SELECT 1"));
    }

    @Test
    void rejectsConfiguredUriBeforeResourceConstructionOrDatasourceResolution() {
        String secret = "private-bootstrap-token";
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.data-source", "unused",
                "data.persistence-units.jdbc.0.init-script.uri", "not a URI " + secret)));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(
                () -> {
                    throw new AssertionError("URI validation must precede datasource resolution");
                },
                () -> config,
                new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration does not support a URI value for 'init-script'."));
        assertThat(failure.getMessage(), not(containsString(secret)));
        assertThat(failure.getCause(), nullValue());
    }

    @Test
    void reportsScalarBootstrapScriptConfigurationWithoutInternalDescriptors() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "data.persistence-units.jdbc.0.connection.url", "jdbc:mysql://localhost/test",
                "data.persistence-units.jdbc.0.connection.jdbc-driver-class-name", "com.mysql.cj.jdbc.Driver",
                "data.persistence-units.jdbc.0.drop-script", "drop.sql",
                "data.persistence-units.jdbc.0.init-script", "init.sql")));
        JdbcPersistenceUnitFactory factory = new JdbcPersistenceUnitFactory(List::of,
                                                                            () -> config,
                                                                            new JdbcTransactionConnectionManager());

        DataException failure = assertThrows(DataException.class, factory::services);

        assertThat(failure.getMessage(),
                   is("JDBC persistence unit configuration has invalid values for 'drop-script' and 'init-script'. "
                              + "Each script must use a supported resource configuration."));
        assertThat(failure.getMessage(), not(containsString("unspecified")));
        assertThat(failure.getMessage(), not(containsString("#1")));
    }
}
