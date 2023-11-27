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

package io.helidon.inject.tests.configbeans;

import java.util.List;
import java.util.Map;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.YamlConfigParser;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicConfigBeanTest {

    @Test
    void acceptConfig() {
        Config cfg = Config.builder(
                        ConfigSources.create(
                                Map.of("name", "server",
                                       "port", "8080",
                                       "description", "test",
                                       "pswd", "pwd1",
                                       "cipher-suites.0", "a",
                                       "cipher-suites.1", "b",
                                       "cipher-suites.2", "c",
                                       "headers.0", "header1",
                                       "headers.1", "header2"),
                                "my-simple-config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        TestServerConfig serverConfig = TestServerConfig.create(cfg);
        assertThat(serverConfig.description(),
                   optionalValue(equalTo("test")));
        assertThat(serverConfig.name(),
                   equalTo("server"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
        assertThat(new String(serverConfig.pswd()),
                   equalTo("pwd1"));
        assertThat(serverConfig.toString(),
                   startsWith("TestServerConfig"));
        assertThat(serverConfig.cipherSuites(),
                   contains("a", "b", "c"));
        assertThat(serverConfig.toString(),
                   endsWith("{name=server,port=8080,cipherSuites=[a, b, c],pswd=****}"));

        TestClientConfig clientConfig = TestClientConfig.create(cfg);
        assertThat(clientConfig.name(),
                   equalTo("server"));
        assertThat(clientConfig.port(),
                   equalTo(8080));
        assertThat(new String(clientConfig.pswd()),
                   equalTo("pwd1"));
        assertThat(clientConfig.toString(),
                   startsWith("TestClientConfig"));
        assertThat(clientConfig.cipherSuites(),
                   contains("a", "b", "c"));
        assertThat(clientConfig.headers(),
                   hasEntry("0", "header1"));
        assertThat(clientConfig.headers(),
                   hasEntry("1", "header2"));
    }

    @Test
    void emptyConfig() {
        Config cfg = Config.create();
        // port is required
        assertThrows(Errors.ErrorMessagesException.class, () -> TestServerConfig.create(cfg));
    }

    @Test
    void onlyRequiredConfig() {
        Config cfg = Config.create(ConfigSources.create(Map.of("port", "8080")));
        TestServerConfig serverConfig = TestServerConfig.create(cfg);
        assertThat(serverConfig.description(),
                   optionalEmpty());
        assertThat(serverConfig.name(),
                   equalTo("default"));
        assertThat(serverConfig.port(),
                   equalTo(8080));
    }

    /**
     * Callers can conceptually use config beans as just plain old vanilla builders, void of any config usage.
     */
    @Test
    void noConfig() {
        TestServerConfig serverConfig = TestServerConfig.builder()
                .port(0) // explicitly set as required - must be set
                .build();
        assertThat(serverConfig.description(), optionalEmpty());
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
                .port(0) // explicitly set as required - must be set
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

    @Test
    void equality() {
        Config cfg = Config.builder()
                .sources(ConfigSources.classpath("io/helidon/builder/config/test/basic-config-bean-test.yaml"))
                .addParser(YamlConfigParser.create())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Config serverCfg = cfg.get("test-server");
        TestServerConfig.Builder serverConfigBeanManualBuilder = TestServerConfig.builder()
                .port(serverCfg.get("port").asInt().get());
        serverCfg.get("name").asString().ifPresent(serverConfigBeanManualBuilder::name);
        serverCfg.get("pswd").asString().ifPresent(serverConfigBeanManualBuilder::pswd);
        serverCfg.get("description").asString().ifPresent(serverConfigBeanManualBuilder::description);
        TestServerConfig serverConfigBeanManual = serverConfigBeanManualBuilder.build();

        Config clientCfg = cfg.get("test-client");
        TestClientConfig.Builder clientConfigBeanManualBuilder = TestClientConfig.builder()
                .port(clientCfg.get("port").asInt().get())
                .serverPort(clientCfg.get("server-port").asInt().get())
                .cipherSuites(clientCfg.get("cipher-suites").asList(String.class).get())
                .headers(clientCfg.get("headers").detach().asMap().get());
        clientCfg.get("name").asString().ifPresent(clientConfigBeanManualBuilder::name);
        clientCfg.get("pswd").asString().ifPresent(serverConfigBeanManualBuilder::pswd);
        TestClientConfig clientConfigBeanManual = clientConfigBeanManualBuilder.build();

        // juxtaposed to the new ConfigBean approach
        TestServerConfig serverConfigBean = TestServerConfig.create(serverCfg);
        TestClientConfig clientConfigBean = TestClientConfig.create(clientCfg);

        assertThat(serverConfigBeanManual, equalTo(serverConfigBean));
        assertThat(clientConfigBeanManual, equalTo(clientConfigBean));
    }

}
