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
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class BasicConfigBeanTest {

    @Test
    void acceptConfig() {
        Config cfg = Config.create(
                ConfigSources.create(
                        Map.of("name", "server",
                               "port", "8080",
                               "description", "test",
                               "pwd", "pwd1"
//                               , "cipher-suites", "a,b,c" // no List mapper available --- todo: discuss w/ tlanger
                        ),
                        "my-simple-config-1"));
        ServerConfig serverConfig = DefaultServerConfig.toBuilder(cfg).build();

        assertThat(serverConfig.description(), optionalValue(equalTo("test")));
        assertThat(serverConfig.name(), equalTo("server"));
        assertThat(serverConfig.port(), equalTo(8080));
        assertThat(new String(serverConfig.pwd()), equalTo("pwd1"));
        assertThat(serverConfig.toString(),
                   startsWith("ServerConfig"));
        assertThat(serverConfig.toString(),
                   endsWith("(name=server, port=8080, cipherSuites=[], pwd=not-null, description=Optional[test])"));
    }

    @Test
    void emptyConfig() {
        Config cfg = Config.create();
        ServerConfig serverConfig = DefaultServerConfig.toBuilder(cfg).build();
        assertThat(serverConfig.description(), optionalEmpty());
        assertThat(serverConfig.name(), equalTo("default"));
        assertThat(serverConfig.port(), equalTo(0));
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
