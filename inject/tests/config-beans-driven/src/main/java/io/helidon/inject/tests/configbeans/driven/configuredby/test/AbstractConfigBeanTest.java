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

package io.helidon.inject.tests.configbeans.driven.configuredby.test;

import java.util.List;
import java.util.Map;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.tests.configbeans.TestClientConfig;
import io.helidon.inject.tests.configbeans.TestServerConfig;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * See {@code BasicConfigBeanTest}, this repeats some of that with a fuller classpath with config-driven-services and full config
 * enabled.  This means that extra validation (e.g., required config attributes, etc.) will be tested here.
 */
public class AbstractConfigBeanTest {

    @Test
    void emptyConfig() {
        Config cfg = Config.create();
        Errors.ErrorMessagesException e = assertThrows(Errors.ErrorMessagesException.class,
                                                       () -> TestServerConfig.builder().config(cfg).build());
        assertThat(e.getMessage(),
                   containsString("\"port\" is required, but not set"));
    }

    @Test
    void minimalConfig() {
        Config cfg = Config.builder(
                        ConfigSources.create(
                                Map.of("port", "8080",
                                       "cipher-suites", "a,b,c",
                                       "headers.0", "header1",
                                       "headers.1", "header2"),
                                "my-simple-config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        TestServerConfig serverConfig = TestServerConfig.builder().config(cfg).build();
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
        assertThat(serverConfig.cipherSuites(),
                   contains("a", "b", "c"));

        TestClientConfig clientConfig = TestClientConfig.builder().config(cfg).build();
        assertThat(clientConfig.pswd(),
                   is(new char[0]));
        assertThat(clientConfig.name(),
                   equalTo("default"));
        assertThat(clientConfig.port(),
                   equalTo(8080));
        assertThat(clientConfig.cipherSuites(),
                   contains("a", "b", "c"));
        assertThat(clientConfig.headers(),
                   hasEntry("0", "header1"));
        assertThat(clientConfig.headers(),
                   hasEntry("1", "header2"));
    }

    /**
     * Callers can conceptually use config beans as just plain old vanilla builders, void of any config usage.
     */
    @Test
    void noConfig() {
        TestServerConfig serverConfig = TestServerConfig.builder()
                .port(0)
                .build();
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(0));
        assertThat(serverConfig.cipherSuites(),
                   equalTo(List.of()));

        serverConfig = TestServerConfig.builder(serverConfig).port(123).build();
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(123));
        assertThat(serverConfig.cipherSuites(),
                   equalTo(List.of()));

        TestClientConfig clientConfig = TestClientConfig.builder()
                .port(0)
                .build();
        assertThat(clientConfig.name(),
                   equalTo("default"));
        assertThat(clientConfig.port(),
                   equalTo(0));
        assertThat(clientConfig.headers(),
                   equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(),
                   equalTo(List.of()));

        clientConfig = TestClientConfig.builder(clientConfig).port(123).build();
        assertThat(clientConfig.name(),
                   equalTo("default"));
        assertThat(clientConfig.port(),
                   equalTo(123));
        assertThat(clientConfig.headers(),
                   equalTo(Map.of()));
        assertThat(clientConfig.cipherSuites(),
                   equalTo(List.of()));
    }

}
