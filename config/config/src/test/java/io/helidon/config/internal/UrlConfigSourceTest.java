/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;
import io.helidon.config.RetryPolicies;
import io.helidon.config.internal.UrlConfigSource.UrlBuilder;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link UrlConfigSource}.
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
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), is(TEST_MEDIA_TYPE));
    }

    @Test
    public void testGetMediaTypeGuessed() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.json"))
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), is(nullValue()));
    }

    @Test
    public void testGetMediaTypeUnknown() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .optional()
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        assertThat(configSource.getMediaType(), is(nullValue()));
    }

    @Test
    public void testLoadNotExists() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
                configSource.init(mock(ConfigContext.class));
                configSource.load();
        });
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));

    }

    @Test
    public void testLoadNotExistsWithRetries() throws MalformedURLException {
        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL("http://config-service/application.unknown"))
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .retryPolicy(RetryPolicies.repeat(2)
                                     .delay(Duration.ofMillis(10))
                                     .delayFactor(2)
                                     .overallTimeout(Duration.ofSeconds(1)))
                .build();

        ConfigException ex = Assertions.assertThrows(ConfigException.class, () -> {
                configSource.init(mock(ConfigContext.class));
                configSource.load();
        });
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testBuilderPollingStrategy() throws MalformedURLException {
        URL url = new URL("http://config-service/application.unknown");
        UrlBuilder builder = (UrlBuilder) ConfigSources.url(url)
                .pollingStrategy(TestingPathPollingStrategy::new);

        assertThat(builder.getPollingStrategyInternal(), instanceOf(TestingPathPollingStrategy.class));
        assertThat(((TestingPathPollingStrategy) builder.getPollingStrategyInternal()).getUrl(), is(url));
    }

    private static class TestingPathPollingStrategy implements PollingStrategy {
        private final URL url;

        public TestingPathPollingStrategy(URL url) {
            this.url = url;

            assertThat(url, notNullValue());
        }

        @Override
        public Flow.Publisher<PollingEvent> ticks() {
            return Flow.Subscriber::onComplete;
        }

        public URL getUrl() {
            return url;
        }
    }

}
