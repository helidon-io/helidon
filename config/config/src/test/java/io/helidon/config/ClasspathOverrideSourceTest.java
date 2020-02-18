/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import io.helidon.config.spi.ConfigContent.OverrideContent;
import io.helidon.config.spi.OverrideSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.ClasspathOverrideSource}.
 */
public class ClasspathOverrideSourceTest {

    @Test
    public void testDescriptionMandatory() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").build();

        assertThat(overrideSource.description(), is("ClasspathOverride[overrides.properties]"));
    }

    @Test
    public void testDescriptionOptional() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").optional().build();

        assertThat(overrideSource.description(), is("ClasspathOverride[overrides.properties]?"));
    }

    @Test
    public void testLoadNotExists() {
        ClasspathOverrideSource overrideSource = OverrideSources.classpath("application.unknown")
                .build();
        ConfigException ex = assertThrows(ConfigException.class, overrideSource::load);
        assertThat(ex.getCause(), instanceOf(ConfigException.class));
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testLoadExists() {
        OverrideSource overrideSource = OverrideSources.classpath("io/helidon/config/overrides.properties")
                .build();

        Optional<OverrideContent> overrideContent = overrideSource.load();
        Optional<OverrideSource.OverrideData> objectNode = overrideContent.map(OverrideContent::data);

        assertThat(objectNode, notNullValue());
        assertThat(objectNode.isPresent(), is(true));
    }

    @Test
    public void testBuilder() {
        OverrideSource overrideSource = OverrideSources.classpath("overrides.properties").build();

        assertThat(overrideSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        OverrideSource overrideSource = OverrideSources.classpath("io/helidon/config/overrides.properties")
                .build();

        assertThat(overrideSource, notNullValue());
    }
}
