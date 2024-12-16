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
package io.helidon.tests.integration.dbclient.mongodb;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Database container utility.
 */
abstract class MongoDBTestContainer {

    private static final ImageFromDockerfile IMAGE = new ImageFromDockerfile("mongodb", false)
            .withFileFromPath(".", Path.of("etc/docker"));

    static final GenericContainer<?> CONTAINER = new GenericContainer<>(IMAGE)
            .withEnv("MONGO_DB", "pokemon")
            .withEnv("MONGO_USER", "test")
            .withEnv("MONGO_PASSWORD", "mongo123")
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage("\\s*Container ready!\\s*", 1)
                    .withStartupTimeout(Duration.ofMinutes(1)));

    static Map<String, Supplier<?>> config() {
        return Map.of("db.connection.url", MongoDBTestContainer::connectionString);
    }

    static String connectionString() {
        return "mongodb://localhost:%s/pokemon".formatted(CONTAINER.getMappedPort(27017));
    }

    private MongoDBTestContainer() {
    }
}
