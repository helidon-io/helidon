/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import java.util.Map;

import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.cdi.Main;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Running multiple servers in parallel.
 */
class ParallelRunTest {
    private static ConfigProviderResolver resolver;
    private static Config emptyConfig;
    private static ClassLoader cl;
    private static Config originalConfig;

    @BeforeAll
    public static void initClass() {
        resolver = ConfigProviderResolver.instance();
        originalConfig = resolver.getConfig();
        emptyConfig = resolver.getBuilder().build();
        cl = Thread.currentThread().getContextClassLoader();
    }

    @AfterAll
    public static void destroyClass() {
        resolver.registerConfig(originalConfig, cl);
    }

    @BeforeEach
    @AfterEach
    public void resetConfig() {
        resolver.registerConfig(emptyConfig, cl);
    }

    @Test
    void testParallelFails() {
        Server server = Server.builder()
                .port(0)
                .build()
                .start();

        try {
            assertThrows(IllegalStateException.class, Server::builder);
        } finally {
            server.stop();
        }
    }

    @Test
    void testParallelCdiFails() {
        Config config = resolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("server.port", "0",
                                                           "mp.initializer.allow", "true")))
                .build();
        resolver.registerConfig(config, cl);

        try {
            Main.main(new String[0]);
            assertThrows(IllegalStateException.class, () -> SeContainerInitializer.newInstance()
                    .initialize());
        } finally {
            Main.shutdown();
        }
    }

    @Test
    void testParallelContainerInitializerFails() {
        Config config = resolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("server.port", "0",
                                                           "mp.initializer.allow", "true")))
                .build();
        resolver.registerConfig(config, cl);

        try {
            SeContainerInitializer.newInstance().initialize();
            assertThrows(IllegalStateException.class, () -> SeContainerInitializer.newInstance()
                    .initialize());
        } finally {
            Main.shutdown();
        }
    }

    @Test
    void testParallelWithCdiFails() {
        Config config = resolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("server.port", "0")))
                .build();
        resolver.registerConfig(config, cl);

        try {
            Main.main(new String[0]);
            assertThrows(IllegalStateException.class, Server::builder);
        } finally {
            Main.shutdown();
        }
    }
}
