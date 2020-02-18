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

package io.helidon.config;

import java.util.Optional;

import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link io.helidon.config.ClasspathConfigSource}.
 */
public class ClasspathConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";

    @Test
    public void testDescriptionMandatory() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").build();

        assertThat(configSource.description(), is("ClasspathConfig[application.conf]"));
    }

    @Test
    public void testDescriptionOptional() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").optional().build();

        assertThat(configSource.description(), is("ClasspathConfig[application.conf]?"));
    }

    @Test
    public void testGetMediaTypeSet() {
        ClasspathConfigSource configSource = ConfigSources.classpath("application.conf")
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        assertThat(configSource.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
    }

    @Test
    public void testGetMediaTypeGuessed() {
        ClasspathConfigSource configSource = ConfigSources.classpath("logging.properties")
                .optional()
                .build();

        assertThat(configSource.load().get().mediaType(), is(Optional.of("text/x-java-properties")));
    }

    @Test
    public void testGetMediaTypeUnknown() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.unknown")
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testLoadNotExists() {
        ClasspathConfigSource configSource = (ClasspathConfigSource) ConfigSources.classpath("application.unknown")
                .build();

        assertThat(configSource.load(), is(Optional.empty()));
    }

    @Test
    public void testLoadExists() {
        ConfigSource configSource = ConfigSources.classpath("io/helidon/config/application.conf")
                .mediaType("application/hocon")
                .build();
    }

    @Test
    public void testBuilder() {
        ConfigSource configSource = ConfigSources.classpath("application.conf").build();

        assertThat(configSource, notNullValue());
    }

    @Test
    public void testBuilderWithMediaType() {
        ConfigSource configSource = ConfigSources.classpath("io/helidon/config/application.conf")
                .mediaType("application/hocon")
                .build();

        assertThat(configSource, notNullValue());
    }
}
