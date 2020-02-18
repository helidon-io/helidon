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

package io.helidon.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import io.helidon.config.spi.OverrideSource;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link io.helidon.config.UrlOverrideSource}.
 */
public class UrlOverrideSourceTest {

    @Test
    public void testDescriptionMandatory() throws MalformedURLException {
        OverrideSource overrideSource = OverrideSources.url(new URL("http://config-service/application.json")).build();

        assertThat(overrideSource.description(), Is.is("UrlOverride[http://config-service/application.json]"));
    }

    @Test
    public void testDescriptionOptional() throws MalformedURLException {
        OverrideSource overrideSource = OverrideSources.url(new URL("http://config-service/application.json"))
                .optional()
                .build();

        assertThat(overrideSource.description(), Is.is("UrlOverride[http://config-service/application.json]?"));
    }

    @Test
    public void testLoadNotExists() throws MalformedURLException {
        UrlOverrideSource overrideSource = OverrideSources
                .url(new URL("http://config-service/application.unknown"))
                .build();

        ConfigException ex = assertThrows(ConfigException.class, overrideSource::load);
        assertThat(ex.getCause(), instanceOf(UnknownHostException.class));
        assertThat(ex.getMessage(), containsString("config-service"));
    }
}
