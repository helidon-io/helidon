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

import io.helidon.builder.api.Prototype;
import io.helidon.config.ConfigException;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.internal.view.Base2ExponentialHistogramAggregation;

class Base2ExponentialHistogramAggregationSupport {

    static class BuilderDecorator implements Prototype.BuilderDecorator<Base2ExponentialHistogramAggregationConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(Base2ExponentialHistogramAggregationConfig.BuilderBase<?, ?> target) {
            /*
            The OTel default values for maxBuckets and maxScale are not publicly accessible. Rather than declare
            defaults for these settings in our config based on the current non-public internal defaults in OTel which could
            change over time, our config insists that users who specify one specify both. Other config code relies on this.
             */
            if (target.maxBuckets().isPresent() != target.maxScale().isPresent()) {
                throw new ConfigException("Max buckets and max scale must either both be present or both be absent");
            }
        }
    }

    static class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Returns an {@link io.opentelemetry.sdk.metrics.Aggregation} instance derived from the config settings.
         *
         * @param config aggregation config
         * @return {@code Aggregation} based on the configuration
         */
        @Prototype.PrototypeMethod
        static Aggregation aggregation(Base2ExponentialHistogramAggregationConfig config) {
            return config.maxBuckets().isPresent()
                    ? Base2ExponentialHistogramAggregation.create(config.maxBuckets().get(),
                                                                  config.maxScale().get())
                    : Base2ExponentialHistogramAggregation.getDefault();
        }
    }
}
