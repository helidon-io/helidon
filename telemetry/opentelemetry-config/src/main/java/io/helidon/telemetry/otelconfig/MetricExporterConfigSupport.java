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

import java.util.function.Function;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.config.EnumMapperProvider;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;

class MetricExporterConfigSupport {

    private static final Function<Config, MetricTemporalityPreferenceType> TEMPORALITY_PREFERENCE_MAPPER =
            new EnumMapperProvider().mapper(MetricTemporalityPreferenceType.class).orElseThrow();

    private MetricExporterConfigSupport() {
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        @Prototype.ConfigFactoryMethod
        static AggregationTemporalitySelector createTemporalityPreference(Config config) {
            var preference = TEMPORALITY_PREFERENCE_MAPPER.apply(config);
            return preference.selector();
        }

        @Prototype.ConfigFactoryMethod
        static DefaultAggregationSelector createDefaultHistogramAggregation(Config config) {
            var aggregationType = MetricDefaultHistogramAggregationConfig.create(config).type();
            return switch (aggregationType) {
                case EXPLICIT_BUCKET_HISTOGRAM -> {
                    var explicitBucketConfig = ExplicitBucketHistogramAggregationConfig.create(config);
                    yield (explicitBucketConfig.bucketBoundaries().isEmpty())
                            ? instrumentType -> Aggregation.explicitBucketHistogram()
                            : instrumentType -> Aggregation.explicitBucketHistogram(explicitBucketConfig.bucketBoundaries());
                }
                case BASE2_EXPONENTIAL_BUCKET_HISTOGRAM -> {
                    var base2Config = Base2ExponentialHistogramAggregationConfig.create(config);
                    /*
                    The base2 config makes sure that maxBuckets and maxScale are either both present or both absent
                    so here we can check only one.
                     */
                    yield instrumentType -> base2Config.maxBuckets().isPresent()
                            ? Aggregation.base2ExponentialBucketHistogram(base2Config.maxBuckets().get(),
                                                                          base2Config.maxScale().get())
                            : Aggregation.base2ExponentialBucketHistogram();
                }
            };
        }

    }
}
