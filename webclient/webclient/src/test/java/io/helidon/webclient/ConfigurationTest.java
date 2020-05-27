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
package io.helidon.webclient;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link WebClientConfiguration}.
 */
public class ConfigurationTest {

    @Test
    public void testWebClientConfigurationFromConfig() {
        Config config = Config.create();
        WebClientConfiguration wcc = WebClientConfiguration.builder()
                .config(config.get("test-client"))
                .build();
        validateConfiguration(wcc);
    }

    @Test
    public void testWebClientConfigurationFromBuilder() {
        WebClientConfiguration wcc = WebClientConfiguration.builder()
                .uri(URI.create("http://some.address:80"))
                .connectTimeout(Duration.of(4000, ChronoUnit.MILLIS))
                .readTimeout(Duration.of(5000, ChronoUnit.MILLIS))
                .followRedirects(true)
                .maxRedirects(10)
                .userAgent("HelidonTest")
                .defaultHeader(Http.Header.ACCEPT, List.of("application/json", "text/plain"))
                .build();
        validateConfiguration(wcc);
    }

    private void validateConfiguration(WebClientConfiguration wcc) {
        assertThat(wcc.uri(), is(URI.create("http://some.address:80")));
        assertThat(wcc.connectTimeout(), is(Duration.of(4000, ChronoUnit.MILLIS)));
        assertThat(wcc.readTimout(), is(Duration.of(5000, ChronoUnit.MILLIS)));
        assertThat(wcc.followRedirects(), is(true));
        assertThat(wcc.maxRedirects(), is(10));
        assertThat(wcc.userAgent(), is("HelidonTest"));
        assertThat(wcc.headers().acceptedTypes(), containsInAnyOrder(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
    }

}
