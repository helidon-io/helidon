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
package io.helidon.webserver;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorLogConfigTest {
    @Test
    void defaults() {
        ErrorLogConfig config = ErrorLogConfig.create();
        assertThat(config.enabled(), is(true));
        assertThat(config.window(), is(Duration.ofHours(1)));
        assertThat(config.reportInterval(), is(Duration.ofMinutes(10)));
        assertThat(config.maxPerError(), is(1));
        assertThat(config.maxTotal(), is(100));
        assertThat(config.maxErrors(), is(100));
        assertThat(WebServerConfig.create().errorLog(), is(config));
    }

    @Test
    void externalConfigurationMatchesBuilder() {
        Config external = Config.just(ConfigSources.create(Map.of(
                "server.error-log.enabled", "false",
                "server.error-log.window", "PT2S",
                "server.error-log.report-interval", "PT1S",
                "server.error-log.max-per-error", "3",
                "server.error-log.max-total", "11",
                "server.error-log.max-errors", "7")));
        ErrorLogConfig expected = ErrorLogConfig.builder()
                .enabled(false)
                .window(Duration.ofSeconds(2))
                .reportInterval(Duration.ofSeconds(1))
                .maxPerError(3)
                .maxTotal(11)
                .maxErrors(7)
                .build();
        assertThat(WebServerConfig.builder().config(external.get("server")).buildPrototype().errorLog(), is(expected));
        assertThat(WebServerConfig.builder().errorLog(expected).buildPrototype().errorLog(), is(expected));
    }

    @Test
    void rejectsInvalidWindowsEvenWhenDisabled() {
        for (Duration window : new Duration[] {Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(Long.MAX_VALUE)}) {
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().window(window).build());
            assertThrows(IllegalArgumentException.class,
                         () -> ErrorLogConfig.builder().enabled(false).window(window).build());
        }
        assertThat(ErrorLogConfig.builder().window(Duration.ofNanos(1)).build().window(), is(Duration.ofNanos(1)));
        assertThat(ErrorLogConfig.builder().window(Duration.ofNanos(Long.MAX_VALUE)).build().window(),
                   is(Duration.ofNanos(Long.MAX_VALUE)));
    }

    @Test
    void rejectsNonPositiveLimits() {
        for (int invalid : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().maxPerError(invalid).build());
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().maxTotal(invalid).build());
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().maxErrors(invalid).build());
        }
    }

    @Test
    void rejectsInvalidReportIntervalsEvenWhenDisabled() {
        for (Duration interval : new Duration[] {Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(Long.MAX_VALUE)}) {
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().reportInterval(interval).build());
            assertThrows(IllegalArgumentException.class,
                         () -> ErrorLogConfig.builder().enabled(false).reportInterval(interval).build());
        }
        assertThat(ErrorLogConfig.builder().reportInterval(Duration.ofNanos(1)).build().reportInterval(),
                   is(Duration.ofNanos(1)));
        assertThat(ErrorLogConfig.builder().reportInterval(Duration.ofNanos(Long.MAX_VALUE)).build().reportInterval(),
                   is(Duration.ofNanos(Long.MAX_VALUE)));
    }

    @Test
    void collectionWindowAndReportIntervalHaveIndependentDurations() {
        for (Duration interval : List.of(Duration.ofNanos(1), Duration.ofNanos(100), Duration.ofNanos(250))) {
            ErrorLogConfig config = ErrorLogConfig.builder()
                    .window(Duration.ofNanos(100))
                    .reportInterval(interval)
                    .build();
            assertThat(config.reportInterval(), is(interval));
        }
    }

    @Test
    void externalConfigurationIsValidated() {
        for (Map<String, String> values : List.of(
                Map.of("window", "PT0S"),
                Map.of("report-interval", "PT0S"),
                Map.of("max-per-error", "0"),
                Map.of("max-total", "-1"),
                Map.of("max-errors", "0"))) {
            Config config = Config.just(ConfigSources.create(values));
            assertThrows(IllegalArgumentException.class, () -> ErrorLogConfig.builder().config(config).build());
        }
    }
}
