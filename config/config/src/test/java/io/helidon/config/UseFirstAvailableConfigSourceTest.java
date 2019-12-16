/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.util.List;
import java.util.Optional;

import io.helidon.config.spi.ConfigContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link UseFirstAvailableConfigSource}.
 */
public class UseFirstAvailableConfigSourceTest {

    @Test
    public void testDescriptionAvailable() {
        UseFirstAvailableConfigSource configSource = new UseFirstAvailableConfigSource(
                ConfigSources.classpath("application.yaml").optional().build(),
                ConfigSources.classpath("io/helidon/config/application.properties").optional().build(),
                ConfigSources.classpath("io/helidon/config/application.conf").build(),
                ConfigSources.classpath("io/helidon/config/application.json").optional().build()
        );

        assertThat(configSource.description(),
                   stringContainsInOrder(List.of(
                           "ClasspathConfig[application.yaml]?->ClasspathConfig[",
                           "io/helidon/config/application.properties]?->ClasspathConfig[",
                           "io/helidon/config/application.conf]->ClasspathConfig[",
                           "io/helidon/config/application.json]?")));

        //whenever loaded mark (empty), *used* and ?ignored? config sources
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(any())).thenReturn(Optional.of(ConfigParsers.properties()));
        configSource.init(context);
        configSource.load();
        assertThat(configSource.description(),
                   stringContainsInOrder(List.of(
                           "(ClasspathConfig[application.yaml]?)->*ClasspathConfig[",
                           "io/helidon/config/application.properties]?*->/ClasspathConfig[",
                           "io/helidon/config/application.conf]/->/ClasspathConfig[",
                           "io/helidon/config/application.json]?/")));
    }

    @Test
    public void testDescriptionNotAvailable() {
        UseFirstAvailableConfigSource configSource = new UseFirstAvailableConfigSource(
                ConfigSources.classpath("application.yaml").optional().build(),
                ConfigSources.classpath("application.conf").optional().build(),
                ConfigSources.classpath("application.json").optional().build(),
                ConfigSources.classpath("application.properties").optional().build()
        );

        assertThat(configSource.description(),
                   is("ClasspathConfig[application.yaml]?->"
                              + "ClasspathConfig[application.conf]?->"
                              + "ClasspathConfig[application.json]?->"
                              + "ClasspathConfig[application.properties]?"));

        //whenever loaded mark (empty), *used* and ?ignored? config sources
        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser(any())).thenReturn(Optional.of(ConfigParsers.properties()));
        configSource.init(context);
        configSource.load();
        assertThat(configSource.description(),
                   is("(ClasspathConfig[application.yaml]?)->"
                              + "(ClasspathConfig[application.conf]?)->"
                              + "(ClasspathConfig[application.json]?)->"
                              + "(ClasspathConfig[application.properties]?)"));
    }

}
