/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

final class OpenTelemetryConfigSupport {

    private OpenTelemetryConfigSupport() {
    }

    private static OpenTelemetry openTelemetry(OpenTelemetryConfig.BuilderBase<?, ?> target) {
        var openTelemetrySdkBuilder = OpenTelemetrySdk.builder();

        if (!target.propagators().isEmpty()) {
            openTelemetrySdkBuilder.setPropagators(ContextPropagators.create(
                    TextMapPropagator.composite(target.propagators())));
        }

        if (target.tracingConfig().isPresent()) {
            var tracingConfig = target.tracingConfig().get();
            var tracingBuilderInfo = tracingConfig.tracingBuilderInfo();
            var sdkTracerProviderBuilder = tracingBuilderInfo.sdkTracerProviderBuilder();

            var attributesBuilder = tracingConfig.tracingBuilderInfo().attributesBuilder();
            attributesBuilder.put(ResourceAttributes.SERVICE_NAME, target.service().orElseThrow());

            var resource = Resource.getDefault().merge(Resource.create(attributesBuilder.build()));
            sdkTracerProviderBuilder.setResource(resource);

            openTelemetrySdkBuilder.setTracerProvider(sdkTracerProviderBuilder.build());
        }

        var sdk = openTelemetrySdkBuilder.build();
        target.openTelemetrySdk(sdk);
        return sdk;
    }

    static class BuildDecorator implements Prototype.BuilderDecorator<OpenTelemetryConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(OpenTelemetryConfig.BuilderBase<?, ?> target) {

            /*
            If the app set the OpenTelemetry instance on the builder explicitly then that overrides what we would
            derive from the settings.
             */
            if (target.openTelemetry().isPresent()) {
                return;
            }

            target.openTelemetry(target.enabled()
                                         ? openTelemetry(target)
                                         : OpenTelemetry.noop());
        }

    }

    static class CustomMethods {

        private CustomMethods() {
        }
        
        /**
         * Converts a config node for propagators into a list of {@link io.opentelemetry.context.propagation.TextMapPropagator}.
         * <p>
         * As a user convenience, the config node can be either a node list (in which case each node's string value will be
         * used for a propagator name) or the node can be a single string containing a comma-separated list of propagator names.
         *
         * @param config config node (node list of string nodes or a single node)
         * @return list of selected propagators
         */
        @Prototype.FactoryMethod
        static List<TextMapPropagator> createPropagators(Config config) {

            Stream<String> propagatorNames = config.isList()
                    ? config.asList(String.class).get().stream()
                    : Arrays.stream(config.asString().get().split(","));

            return propagatorNames
                    .map(ContextPropagationType::from)
                    .map(ContextPropagationType::propagator)
                    .toList();
        }

        @Prototype.FactoryMethod
        static OpenTelemetryTracingConfig createTracingConfig(Config config) {
            return OpenTelemetryTracingConfig.create(config);
        }

    }

}

