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

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SingletonTest {
    private static Config originalConfig;
    private static ConfigProviderResolver configResolver;
    private static ClassLoader cl;
    private static SeContainer container;

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

        container = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(SingletonBean.class)
                .initialize();
    }

    @AfterAll
    static void destroyClass() {
        configResolver.registerConfig(originalConfig, cl);
        if (container != null) {
            container.close();
        }
    }

    @Test
    void testSingleton() throws InterruptedException {
        SingletonBean first = container.select(SingletonBean.class).get();
        Thread.sleep(1);
        SingletonBean second = container.select(SingletonBean.class).get();

        assertThat(first.getTime(), is(second.getTime()));
    }

}
