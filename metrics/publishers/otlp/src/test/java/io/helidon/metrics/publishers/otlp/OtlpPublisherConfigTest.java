/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.publishers.otlp;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OtlpPublisherConfigTest {
    @Test
    void defaults() {
        var publisher = OtlpPublisher.create();

        assertThat(publisher.enabled(), is(true));
        assertThat(publisher.name(), is("otlp"));
        assertThat(publisher.type(), is("otlp"));
        assertThat(publisher.prototype().endpoint(), is(URI.create("http://localhost:4318/v1/metrics")));
        assertThat(publisher.prototype().interval(), is(Duration.ofMinutes(1)));
        assertThat(publisher.prototype().timeout(), is(Duration.ofSeconds(10)));
        assertThat(publisher.prototype().maxRequestSize().toBytes(), is(64L * 1024 * 1024));
        assertThat(publisher.prototype().serviceName(), is("unknown_service"));
        assertThat(publisher.prototype().headers(), is(Map.of()));
        assertThat(publisher.prototype().resourceAttributes(), is(Map.of()));
    }

    @Test
    void componentConfiguration() {
        var config = Config.just(ConfigSources.create(Map.of(
                "publisher.enabled", "false",
                "publisher.name", "configured",
                "publisher.endpoint", "https://collector.example/tenant/metrics?key=value",
                "publisher.interval", "PT2S",
                "publisher.timeout", "PT1S",
                "publisher.max-request-size", "32 KiB",
                "publisher.service-name", "checkout",
                "publisher.headers.Authorization", "Bearer example",
                "publisher.resource-attributes.region", "east")));

        var publisher = OtlpPublisher.create(config.get("publisher"));

        assertThat(publisher.enabled(), is(false));
        assertThat(publisher.name(), is("configured"));
        assertThat(publisher.prototype().endpoint(),
                   is(URI.create("https://collector.example/tenant/metrics?key=value")));
        assertThat(publisher.prototype().interval(), is(Duration.ofSeconds(2)));
        assertThat(publisher.prototype().timeout(), is(Duration.ofSeconds(1)));
        assertThat(publisher.prototype().maxRequestSize().toBytes(), is(32L * 1024));
        assertThat(publisher.prototype().serviceName(), is("checkout"));
        assertThat(publisher.prototype().headers(), is(Map.of("Authorization", "Bearer example")));
        assertThat(publisher.prototype().resourceAttributes(), is(Map.of("region", "east")));
    }

    @Test
    void providerPreservesInstanceName() {
        var publisher = new OtlpPublisherProvider().create(Config.empty(), "collector-east");

        assertThat(publisher, instanceOf(OtlpPublisher.class));
        assertThat(publisher.name(), is("collector-east"));
    }

    @Test
    void rejectsInvalidEndpoint() {
        for (String endpoint : List.of("/v1/metrics", "file:///tmp/metrics", "http://user:secret@localhost/v1/metrics",
                                       "http://localhost/v1/metrics#fragment", "http://localhost:65536/v1/metrics")) {
            assertThrows(IllegalArgumentException.class,
                         () -> OtlpPublisher.builder().endpoint(URI.create(endpoint)).build(),
                         endpoint);
        }
    }

    @Test
    void rejectsUnboundedDurations() {
        for (Duration duration : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(Long.MAX_VALUE))) {
            assertThrows(IllegalArgumentException.class,
                         () -> OtlpPublisher.builder().interval(duration).build(),
                         "interval " + duration);
            assertThrows(IllegalArgumentException.class,
                         () -> OtlpPublisher.builder().timeout(duration).build(),
                         "timeout " + duration);
        }
    }

    @Test
    void acceptsRequestSizeBounds() {
        for (long bytes : List.of(1L, (long) Integer.MAX_VALUE)) {
            var publisher = OtlpPublisher.builder().maxRequestSize(Size.create(bytes)).build();
            assertThat(publisher.prototype().maxRequestSize().toBytes(), is(bytes));
        }
    }

    @Test
    void rejectsInvalidRequestSizes() {
        for (Size size : List.of(Size.ZERO, Size.create(-1), Size.create((long) Integer.MAX_VALUE + 1),
                                 Size.create(Long.MAX_VALUE), Size.create(Long.MAX_VALUE, Size.Unit.KIB))) {
            assertThrows(IllegalArgumentException.class,
                         () -> OtlpPublisher.builder().maxRequestSize(size).build(),
                         "max-request-size " + size);
        }
    }

    @Test
    void rejectsProtocolHeaderOverrides() {
        for (String name : List.of("Content-Type", "Content-Length", "Transfer-Encoding", "Content-Encoding", "Host",
                                   "Connection")) {
            assertThrows(IllegalArgumentException.class,
                         () -> OtlpPublisher.builder().headers(Map.of(name, "override")).build(),
                         name);
        }
    }

    @Test
    void rejectsBlankServiceName() {
        assertThrows(IllegalArgumentException.class, () -> OtlpPublisher.builder().serviceName(" ").build());
    }
}
