/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.telemetry.otelconfig;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.LogLimits;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class TestLoggingConfig {

    @Test
    void testLoggingConfig() {
        var config = Config.just("""
                                         telemetry:
                                           service: test-tel-logging
                                           global: false
                                           signals:
                                             logging:
                                               minimum-severity: TRACE
                                               log-limits:
                                                 max-attribute-value-length: 20
                                                 max-number-of-attributes: 14
                                               processors:
                                                 - type: batch
                                                   schedule-delay: PT10S
                                                   max-queue-size: 15
                                                   max-export-batch-size: 5
                                                   timeout: PT30S
                                                 - type: simple
                                               exporters:
                                                 - name: exp-1
                                                   endpoint: "http://host:1234"
                                         """,
                                 MediaTypes.APPLICATION_YAML);

        var otelConfig = OpenTelemetryConfig.create(config.get("telemetry"));

        assertThat("Logging in OTel config", otelConfig.logging(), OptionalMatcher.optionalPresent());

        OpenTelemetryLoggingConfig loggingConfig = otelConfig.logging().get();

        assertThat("Logging processors", loggingConfig.processors(), hasSize(2));

        assertThat("Logging processor", loggingConfig.processors().getFirst().toString(),
                   allOf(containsString("endpoint=http://host:1234"),
                         containsString("")));

        assertThat("Log limits", loggingConfig.logLimits(),
                   OptionalMatcher.optionalValue(allOf(maxNumberOfAttributes(is(14)),
                                                       maxAttributeValueLength(is(20)))));

        assertThat("Severity", loggingConfig.minimumSeverity(), OptionalMatcher.optionalValue(is(Severity.TRACE)));


    }

    static Matcher<LogLimits> maxNumberOfAttributes(Matcher<? super Integer> matcher) {
        return new FeatureMatcher<LogLimits, Integer>(matcher, "an integer value that ","maxNumberOfAttributes") {
            @Override
            protected Integer featureValueOf(LogLimits actual) {
                return actual.getMaxNumberOfAttributes();
            }
        };
    }

    static Matcher<LogLimits> maxAttributeValueLength(Matcher<? super Integer> matcher) {
        return new FeatureMatcher<LogLimits, Integer>(matcher, "an integer value that ","maxAttributeValueLength") {
            @Override
            protected Integer featureValueOf(LogLimits actual) {
                return actual.getMaxAttributeValueLength();
            }
        };
    }


}
