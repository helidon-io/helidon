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

package io.helidon.microprofile.testing.lra;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Configuration for {@code @HelidonTest} with LRA coordinator running on random port.
 * Any of the properties can be overridden with {@code @AddConfig}.
 * Example of running test coordinator on port 8070:
 * <pre>{@code
 * @HelidonTest
 * @AddConfig(key = "server.sockets.500.port", value = "8070")
 * @AddBean(TestLraCoordinator.class)
 * }</pre>
 */
public class TestLraCoordinatorConfigSource implements ConfigSource {

    private static final String PORT_IDX = System.getProperty("helidon.lra.coordinator.test-socket.index", "500");
    private static final Map<String, String> CONFIG = Map.of(
            // Extra socket for coordinator on random port
            "server.sockets." + PORT_IDX + ".name", TestLraCoordinator.ROUTING_NAME,
            "server.sockets." + PORT_IDX + ".port", "0",
            "server.sockets." + PORT_IDX + ".bind-address", "localhost",
            // Avoid using persistent tx log in test LRA coordinator
            "helidon.lra.coordinator.persistence", "false",
            // Avoid using build time Jandex index
            "helidon.lra.participant.use-build-time-index", "false");

    /**
     * Initialized by service locator.
     */
    public TestLraCoordinatorConfigSource() {
    }

    @Override
    public Set<String> getPropertyNames() {
        return CONFIG.keySet();
    }

    @Override
    public int getOrdinal() {
        return 5000;
    }

    @Override
    public String getValue(String propertyName) {
        return CONFIG.get(propertyName);
    }

    @Override
    public String getName() {
        return TestLraCoordinator.ROUTING_NAME;
    }
}
