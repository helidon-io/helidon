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

import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.internal.view.DefaultAggregation;
import io.opentelemetry.sdk.metrics.internal.view.DropAggregation;
import io.opentelemetry.sdk.metrics.internal.view.LastValueAggregation;
import io.opentelemetry.sdk.metrics.internal.view.SumAggregation;

class ViewRegistrationConfigSupport {

    static class CustomMethods {

        private CustomMethods() {
        }

        /**
         * Creates a {@link io.opentelemetry.sdk.metrics.View} instance based on the config settings.
         *
         * @param viewRegistrationConfig view configuration settings
         * @return {@code View}
         */
        @Prototype.PrototypeMethod
        static View view(ViewRegistrationConfig viewRegistrationConfig) {
            var builder = View.builder();
            viewRegistrationConfig.name().ifPresent(builder::setName);
            viewRegistrationConfig.description().ifPresent(builder::setDescription);
            viewRegistrationConfig.attributeFilter().ifPresent(builder::setAttributeFilter);

            builder.setAggregation(viewRegistrationConfig.aggregation());

            viewRegistrationConfig.cardinalityLimit()
                    .ifPresent(builder::setCardinalityLimit);

            return builder.build();
        }

        @Prototype.ConfigFactoryMethod
        static Aggregation createAggregation(Config config) {
            var aggregationConfig = AggregationConfig.create(config);
            return switch (aggregationConfig.type()) {
                case DROP -> DropAggregation.getInstance();
                case DEFAULT -> DefaultAggregation.getInstance();
                case SUM -> SumAggregation.getInstance();
                case LAST_VALUE -> LastValueAggregation.getInstance();
                case EXPLICIT_BUCKET_HISTOGRAM -> ExplicitBucketHistogramAggregationConfig.create(config).aggregation();
                case BASE2_EXPONENTIAL_BUCKET_HISTOGRAM ->
                        Base2ExponentialHistogramAggregationConfig.create(config).aggregation();
            };
        }

        @Prototype.ConfigFactoryMethod
        static Predicate<String> createAttributeFilter(Config config) {
            Pattern attributePattern = Pattern.compile(config.asString().get());
            return attributeName -> attributePattern.matcher(attributeName).matches();
        }

        @Prototype.ConfigFactoryMethod
        static InstrumentSelector createInstrumentSelector(Config config) {
            var instrumentSelectorConfig = InstrumentSelectorConfig.create(config);
            var builder = InstrumentSelector.builder();

            instrumentSelectorConfig.meterName().ifPresent(builder::setMeterName);
            instrumentSelectorConfig.meterSchemaUrl().ifPresent(builder::setMeterSchemaUrl);
            instrumentSelectorConfig.meterVersion().ifPresent(builder::setMeterVersion);
            instrumentSelectorConfig.type().ifPresent(builder::setType);
            instrumentSelectorConfig.name().ifPresent(builder::setName);
            instrumentSelectorConfig.unit().ifPresent(builder::setUnit);

            return builder.build();
        }
    }
}
