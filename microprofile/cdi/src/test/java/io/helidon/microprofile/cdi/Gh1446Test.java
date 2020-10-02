/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for problem reported in Github issue #1446.
 * <p>
 * Helidon container starts even if exception is thrown during initialization(after deployment)
 */
public class Gh1446Test {
    private static Config originalConfig;
    private static ConfigProviderResolver configResolver;
    private static ClassLoader cl;

    @BeforeAll
    static void initClass() {
        originalConfig = ConfigProvider.getConfig();
        configResolver = ConfigProviderResolver.instance();
        cl = Thread.currentThread().getContextClassLoader();

        Config config = configResolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp.initializer.allow", "true",
                                                           "mp.initializer.no-warn", "true")))
                .build();

        configResolver.registerConfig(config, cl);
    }

    @AfterAll
    static void destroyClass() {
        configResolver.registerConfig(originalConfig, cl);
    }
    @Test
    void testStartupFails() {

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> SeContainerInitializer.newInstance()
                .addExtensions(new FailingExtension())
                .disableDiscovery()
                .initialize());

        thrown.printStackTrace();
        assertThat(thrown.getMessage(), is("Survive this!"));

        assertThrows(IllegalStateException.class, CDI::current, "There should be no CDI instance available");

    }

    private static class FailingExtension implements Extension {
        void init(@Observes @Priority(PLATFORM_AFTER + 101) @Initialized(ApplicationScoped.class) Object e) {
            throw new RuntimeException("Survive this!");
        }
    }
}
