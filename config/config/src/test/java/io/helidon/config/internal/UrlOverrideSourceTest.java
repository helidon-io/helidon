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

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.OverrideSources;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;

import org.hamcrest.core.Is;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UrlOverrideSource}.
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
        UrlOverrideSource overrideSource = (UrlOverrideSource) OverrideSources
                .url(new URL("http://config-service/application.unknown"))
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .build();

        ConfigException ex = assertThrows(ConfigException.class, () -> {
                overrideSource.load();
        });
        assertTrue(instanceOf(ConfigException.class).matches(ex.getCause()));
        assertTrue(ex.getMessage().startsWith("Cannot load data from mandatory source"));
    }

    @Test
    public void testBuilderPollingStrategy() throws MalformedURLException {
        URL url = new URL("http://config-service/application.unknown");
        UrlOverrideSource.UrlBuilder builder = (UrlOverrideSource.UrlBuilder) OverrideSources.url(url)
                .pollingStrategy(UrlOverrideSourceTest.TestingPathPollingStrategy::new);

        assertThat(builder.getPollingStrategyInternal(), instanceOf(UrlOverrideSourceTest.TestingPathPollingStrategy.class));
        assertThat(((UrlOverrideSourceTest.TestingPathPollingStrategy) builder.getPollingStrategyInternal()).getUrl(),
                   Is.is(url));
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
