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

package io.helidon.telemetry.otelconfig;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentelemetry.sdk.metrics.InstrumentType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

class TestMetricsConfig {

    @Test
    void testExporter() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: "test-telemetry"
                          global: false
                          signals:
                            metrics:
                              exporters:
                                - name: exp-1
                                  endpoint: "http://host:1234"
                                  temporality-preference: cumulative
                                  default-histogram-aggregation:
                                    type: base2-exponential-bucket-histogram
                                    max-buckets: 152
                                    max-scale: 19
                                - name: exp-2
                                  protocol: grpc
                                  temporality-preference: lowmemory
                                  default-histogram-aggregation:
                                    type: explicit-bucket-histogram
                                    bucket-boundaries: [3,5,7]
                              readers:
                                - type: periodic
                                  exporter: exp-1
                                  interval: PT6S
                              views:
                                - name: sum-view
                                  aggregation:
                                    type: sum
                                  description: "Sum view"
                                  instrument-selector:
                                    name: counter-selector
                                    type: counter
                                    meter-name: my-counter
                        """,
                MediaTypes.APPLICATION_YAML));

        OpenTelemetryConfig otelConfig = OpenTelemetryConfig.create(config.get("telemetry"));

        assertThat("Metrics config in OTel config", otelConfig.metricsConfig(), OptionalMatcher.optionalPresent());

        OpenTelemetryMetricsConfig metricsConfig = otelConfig.metricsConfig().get();

        assertThat("Metric readers", metricsConfig.readers(), hasSize(1));

        assertThat("Metric reader", metricsConfig.readers().getFirst().toString(),
                   allOf(containsString("intervalNanos=6000000000"),
                         containsString("endpoint=http://host:1234"),
                         containsString("PeriodicMetricReader{")));

        assertThat("Default aggregation in reader",
                   metricsConfig.exporters()
                           .get("exp-1")
                           .getDefaultAggregation(InstrumentType.COUNTER).toString(),
                   allOf(containsString("maxBuckets=152"),
                         containsString("maxScale=19")));

        assertThat("Other exporter", metricsConfig.exporters().get("exp-2").toString(),
                   allOf(containsString("OtlpGrpcMetricExporter{"),
                         containsString("endpoint=http://localhost:4317")));

        assertThat("Views", metricsConfig.viewRegistrations(), hasSize(1));
        assertThat("View", metricsConfig.viewRegistrations().getFirst().view().toString(),
                   allOf(containsString("name=sum-view"),
                         containsString("description=Sum view"),
                         containsString("aggregation=SumAggregation")));
        assertThat("Instrument selector", metricsConfig.viewRegistrations().getFirst().instrumentSelector().toString(),
                   allOf(containsString("instrumentType=COUNTER"),
                         containsString("instrumentName=counter-selector"),
                         containsString("meterName=my-counter")));

    }
}
