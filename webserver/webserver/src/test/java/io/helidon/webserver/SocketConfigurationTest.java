/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.present;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class SocketConfigurationTest {
    private static final String ERROR_PREFIX = "Config multiport/application.yaml ";
    private static Config deprecated;
    private static Config current;
    private static Config runnable;
    private static Config noname;

    @BeforeAll
    static void initClass() {
        Config config = Config.create(ConfigSources.classpath("multiport/application.yaml"));
        deprecated = config.get("sockets.deprecated.server");
        current = config.get("sockets.current.server");
        runnable = config.get("sockets.runnable.server");
        noname = config.get("sockets.noname.server");
    }

    @Test
    void testNoName() {
        Assertions.assertThrows(ConfigException.class, () -> WebServer.builder()
                .host("localhost")
                .config(noname)
                .build());
    }

    @Test
    void testDeprecatedConfig() {
        validateConfiguration("deprecated", deprecated);
    }

    @Test
    void testCurrentConfig() {
        validateConfiguration("current", current);
    }

    @Test
    void testRunnableConfig() throws ExecutionException, InterruptedException {
        WebServer server = WebServer.builder()
                .host("localhost")
                .config(runnable)
                .build()
                .start()
                .toCompletableFuture()
                .get();

        try {
            ServerConfiguration configuration = server.configuration();

            validateRunnableSocket(WebServer.DEFAULT_SOCKET_NAME, configuration, true);
            validateRunnablePort(WebServer.DEFAULT_SOCKET_NAME, server, true);

            Optional<SocketConfiguration> maybeConfig = configuration.namedSocket("admin");
            assertThat(ERROR_PREFIX + " runnable admin socket must be configured",
                       maybeConfig,
                       present());
            validateRunnableSocket("admin", maybeConfig.get(), true);
            validateRunnablePort("admin", server, true);

            maybeConfig = configuration.namedSocket("static");
            assertThat(ERROR_PREFIX + " runnable static socket must be configured",
                       maybeConfig,
                       present());
            validateRunnableSocket("static", maybeConfig.get(), false);
            validateRunnablePort("static", server, false);
        } finally {
            server.shutdown()
                    .toCompletableFuture()
                    .get();
        }

    }

    private void validateRunnablePort(String socketName, WebServer server, boolean enabled) {
        if (enabled) {
            assertThat(ERROR_PREFIX + " runnable \"" + socketName + "\" port must be an ephemeral port",
                       server.port(socketName),
                       not(0));
        } else {
            assertThat(ERROR_PREFIX + " runnable \"" + socketName + "\" port must be disabled, yet running",
                       server.port(socketName),
                       // -1 is the value webserver returns when socket is not known or not active
                       is(-1));
        }
    }

    private void validateRunnableSocket(String name, SocketConfiguration socketConfig, boolean enabled) {
        assertThat(socketConfig.name(), is(name));
        assertThat(socketConfig.enabled(), is(enabled));
    }

    private void validateConfiguration(String type, Config config) {
        WebServer server = WebServer.builder()
                .host("localhost")
                .config(config)
                .build();

        ServerConfiguration configuration = server.configuration();
        assertThat(ERROR_PREFIX + type + " socket configuration from multiport/application.yaml default server port",
                   configuration.port(),
                   is(8000));

        // socket admin, port 8001
        validateSocket(type, configuration, "admin", 8001, true);

        // socket static, port 8002, enabled false
        validateSocket(type, configuration, "static", 8002, false);
    }

    private void validateSocket(String type, ServerConfiguration configuration, String name, int port, boolean enabled) {
        Optional<SocketConfiguration> socketConfiguration = configuration.namedSocket(name);
        assertThat(ERROR_PREFIX + type + " " + name + " socket must be configured",
                socketConfiguration,
                   present());

        SocketConfiguration socket = socketConfiguration.get();
        assertThat(ERROR_PREFIX + type + " " + name + " socket port",
                   socket.port(),
                   is(port));

        assertThat(ERROR_PREFIX + type + " " + name + " socket enabled",
                   socket.enabled(),
                   is(enabled));
    }
}
