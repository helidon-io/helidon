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
package io.helidon.microprofile.testing.junit5;

import java.lang.reflect.Proxy;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;

import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link HelidonTestConfig}.
 */
class HelidonTestConfigTest {

    @Test
    void testResolve() {
        // install the "original" config
        ConfigProviderResolver resolver = ConfigProviderResolver.instance();
        resolver.registerConfig(resolver.getBuilder().build(), getClass().getClassLoader());

        HelidonTestConfig config = new HelidonTestConfig(new HelidonTestInfo.ClassInfo(getClass()));
        config.synthetic().update(addConfig("key1", "value1"));
        config.synthetic().update(addConfig("key2", "value2"));

        // synthetic config does not resolve
        assertThat(config.getOptionalValue("key1", String.class).isPresent(), is(false));
        assertThat(config.getOptionalValue("key2", String.class).isPresent(), is(false));

        // switch to synthetic
        config.resolve();

        assertThat(config.getValue("key1", String.class), is("value1"));
        assertThat(config.getValue("key2", String.class), is("value2"));

        // update synthetic config
        assertThat(config.getOptionalValue("complex.key1", String.class).isPresent(), is(false));
        config.synthetic().update(configuration(false, "test-config.yaml"));
        assertThat(config.getValue("complex.key1", String.class), is("complex-value1"));

        // useExisting=true
        config.synthetic().update(configuration(true));

        // switch to original
        config.resolve();

        // synthetic config does not resolve
        assertThat(config.getOptionalValue("key1", String.class).isPresent(), is(false));
        assertThat(config.getOptionalValue("key2", String.class).isPresent(), is(false));
        assertThat(config.getOptionalValue("complex.key1", String.class).isPresent(), is(false));
    }

    @Test
    void testJustInTime() {
        HelidonTestConfig config = new HelidonTestConfig(new HelidonTestInfo.ClassInfo(getClass()));
        config.resolve();

        // se config
        Config hc = MpConfig.toHelidonConfig(config);

        config.synthetic().update(addConfig("key1", "value1"));
        config.synthetic().update(addConfig("key2", "value2"));
        assertThat(hc.get("key1").asString().get(), is("value1"));
        assertThat(hc.get("key2").asString().get(), is("value2"));

        // update synthetic config
        config.synthetic().update(configuration(false, "test-config.yaml"));
        assertThat(hc.get("complex.key1").asString().get(), is("complex-value1"));
    }

    private static AddConfig addConfig(String key, String value) {
        ClassLoader cl = HelidonTestConfigTest.class.getClassLoader();
        return (AddConfig) Proxy.newProxyInstance(cl, new Class[] {AddConfig.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "key" -> key;
                    case "value" -> value;
                    default -> method.getDefaultValue();
                });
    }

    private static Configuration configuration(boolean useExisting, String... configSources) {
        ClassLoader cl = HelidonTestConfigTest.class.getClassLoader();
        return (Configuration) Proxy.newProxyInstance(cl, new Class[] {Configuration.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "useExisting" -> useExisting;
                    case "configSources" -> configSources;
                    default -> method.getDefaultValue();
                });
    }
}
