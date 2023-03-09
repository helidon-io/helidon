/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.test;

import java.util.Map;

import io.helidon.builder.config.testsubjects.fakes.FakeWebServerTlsConfig;
import io.helidon.config.ConfigSources;
import io.helidon.config.MapConfigSource;

class AbstractConfigBeanTest {
    static final String NESTED = "nested";
    static final String FAKE_SOCKET_CONFIG = "sockets";
    static final String FAKE_SERVER_CONFIG = "fake-server";

    MapConfigSource.Builder createRootDefault8080TestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        FAKE_SERVER_CONFIG + ".port", "8080",
                        FAKE_SERVER_CONFIG + ".worker-count", "1"
                ), "config-root-default-8080");
    }

    MapConfigSource.Builder createRootPlusOneSocketTestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        FAKE_SERVER_CONFIG + ".name", "root",
                        FAKE_SERVER_CONFIG + ".port", "8080",
                        FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".name", "first",
                        FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".port", "8081"
                ), "config-nested-plus-one-socket");
    }

    MapConfigSource.Builder createNestedPlusOneSocketAndOneTlsTestingConfigSource() {
        return ConfigSources.create(
                Map.of(
                        NESTED + "." + FAKE_SERVER_CONFIG + ".name", "nested",
                        NESTED + "." + FAKE_SERVER_CONFIG + ".port", "8080",
                        NESTED + "." + FAKE_SERVER_CONFIG + ".worker-count", "2",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".name", "first",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".port", "8081",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.enabled", "true",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.cipher", "cipher-1",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.enabled-tls-protocols",
                                        FakeWebServerTlsConfig.PROTOCOL
                ), "config-nested-plus-one-socket-and-tls");
    }

    MapConfigSource.Builder createFakeServerConfigFromYamlResourceTestingSource() {
        return ConfigSources.create(
                Map.of(
                        NESTED + "." + FAKE_SERVER_CONFIG + ".name", "nested",
                        NESTED + "." + FAKE_SERVER_CONFIG + ".port", "8080",
                        NESTED + "." + FAKE_SERVER_CONFIG + ".worker-count", "2",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".name", "first",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".port", "8081",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.enabled", "true",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.cipher", "cipher-1",
                        NESTED + "." + FAKE_SERVER_CONFIG + "." + FAKE_SOCKET_CONFIG + ".tls.enabled-tls-protocols",
                        FakeWebServerTlsConfig.PROTOCOL
                ), "config-nested-plus-one-socket-and-tls");
    }

}
