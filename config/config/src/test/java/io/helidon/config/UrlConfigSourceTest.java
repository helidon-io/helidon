/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link io.helidon.config.UrlConfigSource}.
 */
public class UrlConfigSourceTest {

    private static final MediaType TEST_MEDIA_TYPE = MediaTypes.create("my/media/type");

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
    public void testDescriptionOmitsSensitiveUrlParts() throws MalformedURLException {
        ConfigSource configSource = ConfigSources
                .url(new URL("http://user:password@config-service:8080/application.json?token=secret#fragment"))
                .build();

        assertThat(configSource.description(), is("UrlConfig[http://config-service:8080/application.json]"));
    }

    @Test
    public void testDescriptionOmitsSensitiveNestedUrlParts() throws MalformedURLException {
        ConfigSource configSource = ConfigSources
                .url(new URL("jar:http://user:password@config-service:8080/application.jar"
                                     + "?token=secret!/application.json"))
                .build();

        assertThat(configSource.description(),
                   is("UrlConfig[jar:http://config-service:8080/application.jar!/application.json]"));
    }

    @Test
    public void testDescriptionOmitsSensitiveNestedEntryUrlParts() throws MalformedURLException {
        ConfigSource configSource = ConfigSources
                .url(new URL("jar:http://user:password@config-service:8080/application.jar"
                                     + "!/application.json?token=secret#fragment"))
                .build();

        assertThat(configSource.description(),
                   is("UrlConfig[jar:http://config-service:8080/application.jar!/application.json]"));
    }

    @Test
    public void testGetMediaTypeSet() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://config-service/application.json"))
                .optional()
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        assertThat(configSource.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
    }

    @Test
    public void testGetMediaTypeGuessed() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://config-service/application.json"))
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testGetMediaTypeUnknown() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .optional()
                .build();

        assertThat(configSource.mediaType(), is(Optional.empty()));
    }

    @Test
    public void testLoadNotExists() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .build();

        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        ConfigException ex = assertThrows(ConfigException.class, runtime::load);
        assertThat(ex.getMessage(), startsWith("Cannot load data from mandatory source: "));
    }

    @Test
    public void testLoadNotExistsOmitsSensitiveUrlParts() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("http://user:password@config-service:8080/application.unknown?token=secret#fragment"))
                .build();

        BuilderImpl.ConfigContextImpl context = mock(BuilderImpl.ConfigContextImpl.class);
        ConfigSourceRuntimeImpl runtime = new ConfigSourceRuntimeImpl(context, configSource);

        ConfigException ex = assertThrows(ConfigException.class, runtime::load);
        assertThat(ex.getMessage(), containsString("http://config-service:8080/application.unknown"));
        assertThat(ex.getMessage(), not(containsString("password")));
        assertThat(ex.getMessage(), not(containsString("token=secret")));
        assertThat(ex.getMessage(), not(containsString("fragment")));
    }

    @Test
    public void testLoadFailureMessageOmitsSensitiveUrlPartsAndPreservesCause(@TempDir Path directory)
            throws MalformedURLException {
        URL url = new URL("jar:" + directory.resolve("missing.jar").toUri()
                                  + "?token=secret!/application.json");

        UrlConfigSource configSource = ConfigSources.url(url).build();

        ConfigException ex = assertThrows(ConfigException.class, configSource::load);
        assertThat(ex.getMessage(), containsString("missing.jar!/application.json"));
        assertThat(ex.getMessage(), not(containsString("token=secret")));
        assertThat(ex.getCause(), notNullValue());
    }

    @Test
    public void testRelativeResolverFailureMessageOmitsSensitiveNestedUrlParts() throws MalformedURLException {
        UrlConfigSource configSource = ConfigSources
                .url(new URL("jar:http://user:password@config-service:8080/application.jar"
                                     + "!/config/application.yaml?token=secret#fragment"))
                .build();

        ConfigException ex = assertThrows(ConfigException.class,
                                          () -> configSource.relativeResolver().apply("include.yaml"));
        assertThat(ex.getMessage(),
                   containsString("jar:http://config-service:8080/application.jar!/config/application.yaml"));
        assertThat(ex.getMessage(), containsString("include.yaml"));
        assertThat(ex.getMessage(), not(containsString("password")));
        assertThat(ex.getMessage(), not(containsString("token=secret")));
        assertThat(ex.getMessage(), not(containsString("fragment")));
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
