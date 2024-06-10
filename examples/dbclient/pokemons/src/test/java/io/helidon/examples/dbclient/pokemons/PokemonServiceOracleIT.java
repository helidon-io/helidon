/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.dbclient.pokemons;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static io.helidon.config.ConfigSources.classpath;

@Testcontainers(disabledWithoutDocker = true)
public class PokemonServiceOracleIT extends AbstractPokemonServiceTest {

    private static final DockerImageName image = DockerImageName.parse("wnameless/oracle-xe-11g-r2")
            .asCompatibleSubstituteFor("gvenzl/oracle-xe");

    @Container
    static OracleContainer container = new OracleContainer(image)
            .withExposedPorts(1521, 8080)
            .withDatabaseName("XE")
            .usingSid()
            .waitingFor(Wait.forListeningPorts(1521, 8080));

    @BeforeAll
    static void setup() {
        Config.global(Config.builder()
                .addSource(ConfigSources.create(Map.of("db.connection.url", container.getJdbcUrl())))
                .addSource(classpath("application-oracle-test.yaml"))
                .build());
        beforeAll();
    }

    @AfterAll
    static void stop() {
        afterAll();
    }
}
