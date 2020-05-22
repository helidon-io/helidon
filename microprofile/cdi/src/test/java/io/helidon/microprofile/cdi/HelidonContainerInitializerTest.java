/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cdi;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link HelidonContainerInitializer}.
 */
class HelidonContainerInitializerTest {
    private static ConfigProviderResolver configResolver;
    private static ClassLoader cl;
    private static org.eclipse.microprofile.config.Config defaultConfig;

    @BeforeAll
    static void initClass() {
        configResolver = ConfigProviderResolver.instance();
        cl = Thread.currentThread().getContextClassLoader();
        defaultConfig = configResolver.getBuilder().build();
    }

    @AfterEach
    void resetConfig() {
        // restore the config to default
        configResolver.registerConfig(defaultConfig, cl);
    }

    @Test
    void testRestart() {
        // this is a reproducer for bug 1554
        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of(HelidonContainerInitializer.CONFIG_ALLOW_INITIALIZER, "true")))
                .build();

        configResolver.registerConfig((org.eclipse.microprofile.config.Config) config, cl);
        // now we can start using SeContainerInitializer
        SeContainer container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(TestBean.class)
                .initialize();

        container.close();

        try {
            Main.main(new String[0]);
        } finally {
            Main.shutdown();
        }
    }

    @Test
    void testRestart2() {
        // this is a reproducer for bug 1554
        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of(HelidonContainerInitializer.CONFIG_ALLOW_INITIALIZER, "true")))
                .build();

        configResolver.registerConfig((org.eclipse.microprofile.config.Config) config, cl);
        // now we can start using SeContainerInitializer
        SeContainerInitializer seContainerInitializer = SeContainerInitializer.newInstance();
        assertThat(seContainerInitializer, instanceOf(HelidonContainerInitializer.class));
        seContainerInitializer
                .disableDiscovery()
                .addBeanClasses(TestBean.class)
                .initialize();

        ((SeContainer) CDI.current()).close();

        try {
            Main.main(new String[0]);
        } finally {
            Main.shutdown();
        }
    }

    @Test
    void testSeInitializerFails() {

        assertThrows(IllegalStateException.class, SeContainerInitializer::newInstance);

        Config config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources
                                     .create(Map.of(HelidonContainerInitializer.CONFIG_ALLOW_INITIALIZER, "true")))
                .build();

        configResolver.registerConfig(config, cl);

        SeContainerInitializer seContainerInitializer = SeContainerInitializer.newInstance();
        assertThat(seContainerInitializer, instanceOf(HelidonContainerInitializer.class));

        try (SeContainer container = seContainerInitializer.initialize()) {
            // do nothing
        }
    }

    @ApplicationScoped
    public static class TestBean {
        public String test() {
            return "test";
        }
    }
}
