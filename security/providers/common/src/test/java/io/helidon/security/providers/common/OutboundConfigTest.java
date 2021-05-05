/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.common;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.SecurityEnvironment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.config.ConfigValues.simpleValue;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link io.helidon.security.providers.common.OutboundConfig}.
 */
public class OutboundConfigTest {
    private Config configWithDefaults;
    private Config configNoDefaults;

    @BeforeEach
    public void init() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("targets_with_default.conf"))
                .build();

        this.configWithDefaults = config.get("security-provider");

        config = Config.builder()
                .sources(ConfigSources.classpath("targets_no_default.conf"))
                .build();

        this.configNoDefaults = config.get("security-provider");

    }

    @Test
    public void testParsing() {
        OutboundConfig targets = OutboundConfig.create(configWithDefaults, new OutboundTarget[0]);

        List<OutboundTarget> targetList = targets.targets();

        assertThat(targetList, notNullValue());
        assertThat(targetList.size(), is(2));

        OutboundTarget target = targetList.get(0);
        assertThat(target.name(), is("default"));
        assertThat(target.hosts(), hasItems("a.b.com"));
        assertThat(target.transports(), hasItems("https"));
        Optional<Config> targetsConfigOpt = target.getConfig();
        Config targetsConfig = targetsConfigOpt.orElseGet(() -> {
            fail("Config was expected to be non-null");
            return null;
        });
        assertThat(targetsConfig.get("type").asString(), is(simpleValue("S2S")));

        target = targetList.get(1);
        assertThat(target.name(), is("obo"));
        assertThat(target.hosts(), hasItems("b.c.com", "d.e.org"));
        assertThat(target.transports(), hasItems("https"));
        targetsConfigOpt = target.getConfig();
        targetsConfig = targetsConfigOpt.orElseGet(() -> {
            fail("Config was expected to be non-null");
            return null;
        });
        assertThat(targetsConfig.get("type").asString(), is(simpleValue("S2S_OBO")));
    }

    @Test
    public void testWithDefaults() {
        // default value must be overriden by config, so the test is the same as above...
        OutboundTarget defaultValue = OutboundTarget.builder("default").build();

        OutboundConfig targets = OutboundConfig.create(configWithDefaults, new OutboundTarget[] {defaultValue});

        List<OutboundTarget> targetList = targets.targets();

        assertThat(targetList, notNullValue());
        assertThat(targetList.size(), is(2));

        OutboundTarget target = targetList.get(0);
        assertThat(target.name(), is("default"));
        assertThat(target.transports(), hasItems("https"));
        assertThat(target.hosts(), hasItems("a.b.com"));
        Optional<Config> targetsConfigOpt = target.getConfig();
        Config targetsConfig = targetsConfigOpt.orElseGet(() -> {
            fail("Config was expected to be non-null");
            return null;
        });
        assertThat(targetsConfig.get("type").asString(), is(simpleValue("S2S")));

        target = targetList.get(1);
        assertThat(target.name(), is("obo"));
        assertThat(target.transports(), hasItems("https"));
        assertThat(target.hosts(), hasItems("b.c.com", "d.e.org"));
        targetsConfigOpt = target.getConfig();
        targetsConfig = targetsConfigOpt.orElseGet(() -> {
            fail("Config was expected to be non-null");
            return null;
        });
        assertThat(targetsConfig.get("type").asString(), is(simpleValue("S2S_OBO")));
    }

    @Test
    public void testWithDefaultsNoDefaultConfig() {

        // default value must be overridden by config, so the test is the same as above...
        OutboundTarget[] defaultValue = {
                OutboundTarget.builder("default").addTransport("https").addHost("www.google.com").build(),
                OutboundTarget.builder("default2").addTransport("http").addHost("localhost").addHost("127.0.0.1").build()
        };
        OutboundConfig targets = OutboundConfig.create(configNoDefaults, defaultValue);

        List<OutboundTarget> targetList = targets.targets();

        assertThat(targetList, notNullValue());
        // 2 defaults + one from config file
        assertThat(targetList.size(), is(3));

        OutboundTarget target = targetList.get(0);
        assertThat(target.name(), is("default"));
        assertThat(target.transports(), hasItems("https"));
        assertThat(target.hosts(), hasItems("www.google.com"));
        Optional<Config> targetsConfig = target.getConfig();
        assertThat(targetsConfig.isPresent(), is(false));

        target = targetList.get(1);
        assertThat(target.name(), is("default2"));
        assertThat(target.transports(), hasItems("http"));
        assertThat(target.hosts(), hasItems("localhost", "127.0.0.1"));
        targetsConfig = target.getConfig();
        assertThat(targetsConfig.isPresent(), is(false));

        target = targetList.get(2);
        assertThat(target.name(), is("obo"));
        assertThat(target.transports(), hasItems("https"));
        assertThat(target.hosts(), hasItems("b.c.com", "d.e.org"));
        targetsConfig = target.getConfig();

        Config config = targetsConfig.orElseGet(() -> {
            fail("Config was expected to be non-null");
            return null;
        });
        assertThat(config.get("type").asString(), is(simpleValue("S2S_OBO")));
    }

    @Test
    public void testUserScenario() {
        OutboundConfig targets = OutboundConfig.create(configWithDefaults);

        Optional<OutboundTarget> optional = targets.findTarget(buildEnv("https", "a.b.com"));

        Map<String, String> expectedProps = new HashMap<>();
        expectedProps.put("type", "S2S");
        assertThat(optional.isPresent(), is(true));
        optional.ifPresent(t -> validateTarget(t, "default", expectedProps));

        optional = targets.findTarget(buildEnv("iiop", "192.168.1.1"));
        assertThat(optional.isPresent(), is(false));
    }

    @Test
    public void testUserScenarioWithDefaults() {
        OutboundTarget[] defaultValue = {
                OutboundTarget.builder("default").addTransport("https").addHost("www.google.com").build(),
                OutboundTarget.builder("default2").addTransport("http").addHost("localhost").addHost("127.0.0.1").build(),
                //intentionally the same config, to make sure we do this in order
                OutboundTarget.builder("default3").addTransport("http").addHost("localhost").addHost("127.0.0.1").build()
        };
        OutboundConfig targets = OutboundConfig.create(configNoDefaults, defaultValue);

        Optional<OutboundTarget> optional = targets.findTarget(buildEnv("https", "d.e.org"));
        assertThat(optional.isPresent(), is(true));

        Map<String, String> expectedProps = new HashMap<>();
        expectedProps.put("type", "S2S_OBO");
        expectedProps.put("s2s-private-key", "~/.ssh/second_id_rsa");
        expectedProps.put("s2s-certificate", "~/.ssh/second_id_rsa.crt");

        optional.ifPresent(t -> validateTarget(t, "obo", expectedProps));
        expectedProps.clear();

        optional = targets.findTarget(buildEnv("https", "www.google.com"));
        assertThat(optional.isPresent(), is(true));
        optional.ifPresent(t -> validateTarget(t, "default", expectedProps));

        optional = targets.findTarget(buildEnv("http", "localhost"));
        assertThat(optional.isPresent(), is(true));
        optional.ifPresent(t -> validateTarget(t, "default2", expectedProps));
    }

    private void validateTarget(OutboundTarget target, String name, Map<String, String> expectedProps) {
        assertThat(target.name(), is(name));

        if (expectedProps.isEmpty()) {
            return;
        }

        Optional<Config> optConfig = target.getConfig();

        if (!optConfig.isPresent()) {
            fail("Expecting properties to be defined, yet config is empty: " + expectedProps);
        }

        Config config = optConfig.get();

        expectedProps.forEach((key, value) -> {
            Config keyConfig = config.get(key);
            assertThat(keyConfig.asString(), is(simpleValue(value)));
        });
    }

    private SecurityEnvironment buildEnv(String transport, String host) {
        SecurityEnvironment mock = Mockito.mock(SecurityEnvironment.class);
        Mockito.when(mock.transport()).thenReturn(transport);
        Mockito.when(mock.method()).thenReturn("GET");
        Mockito.when(mock.path()).thenReturn(Optional.of(""));
        Mockito.when(mock.targetUri()).thenReturn(URI.create(transport + "://" + host));

        return mock;
    }
}
