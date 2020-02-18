/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.Optional;

import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link io.helidon.config.UrlConfigSource}.
 */
public class UrlConfigSourceTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";

    @Test
    public void testDescriptionMandatory() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.url(new URL("http://config-service/application.json")).build();

        assertThat(configSource.description(), is("UrlConfig[http://config-service/application.json]"));
    }

    @Test
    public void testDescriptionOptional() throws MalformedURLException {
        ConfigSource configSource = ConfigSources.url(new URL("http://config-service/application.json"))
                .optional()
                .build();

        assertThat(configSource.description(), is("UrlConfig[http://config-service/application.json]?"));
    }

    @Test
    public void testGetMediaTypeSet() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.json"))
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        assertThat(configSource.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
    }

    @Test
    public void testGetMediaTypeGuessed() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.json"))
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testGetMediaTypeUnknown() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testLoadNotExists() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .build();

        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        ConfigException ex = assertThrows(ConfigException.class, runtime::load);
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source: "));
    }

    @Test
    public void testLoadNotExistsWithRetries() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .retryPolicy(RetryPolicies.repeat(2)
                                     .delay(Duration.ofMillis(10))
                                     .delayFactor(2)
                                     .overallTimeout(Duration.ofSeconds(1)))
                .build();

        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        ConfigException ex = assertThrows(ConfigException.class, runtime::load);
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source"));
    }
}
