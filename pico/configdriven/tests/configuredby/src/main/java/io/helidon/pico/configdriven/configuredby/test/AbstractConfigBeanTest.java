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

package io.helidon.pico.configdriven.configuredby.test;

import java.util.List;
import java.util.Map;

import io.helidon.builder.config.testsubjects.ClientConfig;
import io.helidon.builder.config.testsubjects.DefaultClientConfig;
import io.helidon.builder.config.testsubjects.DefaultServerConfig;
import io.helidon.builder.config.testsubjects.ServerConfig;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * See {@code BasicConfigBeanTest}, this repeats some of that with a fuller classpath with config-driven-services and full config
 * enabled.  This means that extra validation (e.g., required config attributes, etc.) will be tested here.
 */
public class AbstractConfigBeanTest {

    @Test
    void emptyConfig() {
        Config cfg = Config.create();
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DefaultServerConfig.toBuilder(cfg).build());
        assertThat(e.getMessage(), equalTo("'port' is a required attribute and cannot be null"));
    }

    @Test
    void minimalConfig() {
        Config cfg = Config.create(
                ConfigSources.create(
                        Map.of("port", "8080"),
                        "my-simple-config-1"));
        ServerConfig serverConfig = DefaultServerConfig.toBuilder(cfg).build();
        assertThat(serverConfig.description(), optionalEmpty());
        assertThat(serverConfig.name(), equalTo("default"));
        assertThat(serverConfig.port(), equalTo(8080));
    }

    /**
     * Callers can conceptually use config beans as just plain old vanilla builders, void of any config usage.
     */
    @Test
    void noConfig() {
        ServerConfig serverConfig = DefaultServerConfig.builder().build();
        assertThat(serverConfig.description(), optionalEmpty());
        assertThat(serverConfig.name(), equalTo("default"));
        assertThat(serverConfig.port(), equalTo(0));
        assertThat(serverConfig.cipherSuites(), equalTo(List.of()));

        serverConfig = DefaultServerConfig.toBuilder(serverConfig).port(123).build();
        assertThat(serverConfig.description(), optionalEmpty());
        assertThat(serverConfig.name(), equalTo("default"));
        assertThat(serverConfig.port(), equalTo(123));
        assertThat(serverConfig.cipherSuites(), equalTo(List.of()));

        ClientConfig clientConfig = DefaultClientConfig.builder().build();
        assertThat(clientConfig.name(), equalTo("default"));
        assertThat(clientConfig.port(), equalTo(0));
        assertThat(clientConfig.headers(), equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(), equalTo(List.of()));

        clientConfig = DefaultClientConfig.toBuilder(clientConfig).port(123).build();
        assertThat(clientConfig.name(), equalTo("default"));
        assertThat(clientConfig.port(), equalTo(123));
        assertThat(clientConfig.headers(), equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(), equalTo(List.of()));
    }

}
