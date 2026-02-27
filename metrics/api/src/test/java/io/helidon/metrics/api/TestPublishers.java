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

package io.helidon.metrics.api;

import java.util.stream.Stream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class TestPublishers {

    static Stream<Arguments> checkPublishersPresent() {
        return Stream.of(Arguments.arguments("when no publishers node",
                                             """
                                                     metrics:
                                                       enabled: true
                                                     """,
                                             0, false),
                         Arguments.arguments("when disabled publisher configured", """
                                 metrics:
                                   publishers:
                                     test-publisher:
                                       enabled: false
                                 """, 0, true),
                         Arguments.arguments("when enabled publisher configured",
                                             """
                                                     metrics:
                                                       publishers:
                                                         test-publisher:
                                                           enabled: true
                                                     """, 1, true));

    }

    @ParameterizedTest
    @MethodSource
    void checkPublishersPresent(String testDescr,
                                String configText,
                                int expectedPublishersSize,
                                boolean expectedPublishersConfigured) {
        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML).get("metrics"));
        assertThat("Publishers list " + testDescr, metricsConfig.publishers(), hasSize(expectedPublishersSize));
        assertThat("publishersConfigured setting " + testDescr,
                   metricsConfig.publishersConfigured(),
                   is(expectedPublishersConfigured));
    }
}
