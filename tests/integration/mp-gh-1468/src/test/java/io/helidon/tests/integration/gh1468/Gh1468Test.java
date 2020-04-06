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
package io.helidon.tests.integration.gh1468;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import io.helidon.microprofile.config.MpConfig;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Gh1468Test {
    private static final String EXPECTED_GREETING = "Hi";

    private static SeContainer container;

    @BeforeAll
    static void initClass() {
        container = SeContainerInitializer.newInstance()
                .addBeanClasses(ConfiguredBean.class)
                .initialize();
    }

    @AfterAll
    static void destroyClass() {
        if (null != container) {
            container.close();
        }
    }

    @Test
    void testInjectedMpConfig() {
        ConfiguredBean test = container.select(ConfiguredBean.class).get();

        String greetingFromBean = test.greeting();

        assertThat(greetingFromBean, is(EXPECTED_GREETING));
    }

    @Test
    void testInjectedHelidonConfig() {
        ConfiguredBean test = container.select(ConfiguredBean.class).get();

        String greeting = test.config()
                .get("app.greeting")
                .asString()
                .get();

        assertThat(greeting, is(EXPECTED_GREETING));
    }

    @Test
    void testMpConfig() {
        Config config = ConfigProvider.getConfig();

        String greeting = config.getValue("app.greeting", String.class);

        // we should read the greeting from config source with higher ordinal than default
        // default is 100
        // CustomConfigSource is 200
        assertThat(greeting, is(EXPECTED_GREETING));
    }

    @Test
    void testHelidonConfig() {
        String greeting = ((MpConfig)ConfigProvider.getConfig())
                .helidonConfig()
                .get("app.greeting")
                .asString()
                .get();

        // we should read the greeting from config source with higher ordinal than default
        // default is 100
        // CustomConfigSource is 200
        assertThat(greeting, is(EXPECTED_GREETING));
    }
}