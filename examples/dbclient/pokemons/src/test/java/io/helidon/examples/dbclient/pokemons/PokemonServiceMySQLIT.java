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
package io.helidon.examples.dbclient.pokemons;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.helidon.config.ConfigSources.classpath;

@Testcontainers(disabledWithoutDocker = true)
public class PokemonServiceMySQLIT extends AbstractPokemonServiceTest {

    @Container
    static MySQLContainer<?> container = new MySQLContainer<>("mysql:8.0.36")
            .withUsername("user")
            .withPassword("password")
            .withNetworkAliases("mysql")
            .withDatabaseName("pokemon");

    @BeforeAll
    static void start() {
        Config.global(Config.builder()
                .addSource(ConfigSources.create(Map.of("db.connection.url", container.getJdbcUrl())))
                .addSource(classpath("application-mysql-test.yaml"))
                .build());
        beforeAll();
    }

    @AfterAll
    static void stop() {
        afterAll();
    }

}
