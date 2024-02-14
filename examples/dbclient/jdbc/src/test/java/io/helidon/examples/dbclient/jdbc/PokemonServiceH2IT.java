/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.dbclient.jdbc;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static io.helidon.config.ConfigSources.classpath;

@Testcontainers(disabledWithoutDocker = true)
class PokemonServiceH2IT extends AbstractPokemonServiceTest {
    private static final DockerImageName H2_IMAGE = DockerImageName.parse("nemerosa/h2");

    @Container
    static GenericContainer<?> container = new GenericContainer<>(H2_IMAGE)
            .withExposedPorts(9082)
            .waitingFor(Wait.forLogMessage("(.*)Web Console server running at(.*)", 1));

    @BeforeAll
    static void start() {
        String url = String.format("jdbc:h2:tcp://localhost:%s/~./test", container.getMappedPort(9082));
        Config.global(Config.builder()
                .addSource(ConfigSources.create(Map.of("db.connection.url", url)))
                .addSource(classpath("application-h2-test.yaml"))
                .build());
        beforeAll();
    }

    @AfterAll
    static void stop() {
        afterAll();
    }
}
