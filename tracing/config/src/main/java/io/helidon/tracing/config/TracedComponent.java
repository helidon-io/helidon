/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tracing.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;

/**
 * A component is a single "layer" of the application that can trace.
 * Component examples:
 * <ul>
 *     <li>web-server: webserver adds the root tracing span + two additional spans (content-read and content-write)</li>
 *     <li>security: security adds the overall request security span, a span for authentication ("security:atn"), a span for
 *          authorization "security:atz", and a span for response processing ("security:response")</li>
 *     <li>jax-rs: JAX-RS integration adds spans for overall resource invocation</li>
 * </ul>
 */
public interface TracedComponent extends Traced {
    /**
     * Disabled component - all subsequent calls return disabled spans and logs.
     */
    TracedComponent DISABLED = TracedComponent.builder().enabled(false).build();
    /**
     * Enabled component - all subsequent calls return enabled spans and logs.
     */
    TracedComponent ENABLED = TracedComponent.builder().build();

    /**
     * Merge configuration of two traced components. This enabled hierarchical configuration
     * with common, default configuration in one traced component and override in another.
     *
     * @param older the older configuration with "defaults"
     * @param newer the newer configuration to override defaults in older
     * @return merged component
     */
    static TracedComponent merge(TracedComponent older, TracedComponent newer) {
        return new TracedComponent() {
            @Override
            public Optional<TracedSpan> span(String spanName) {
                if (!enabled().orElse(true)) {
                    return TracedConfigUtil.DISABLED_SPAN;
                }

                Optional<TracedSpan> newSpan = newer.span(spanName);
                Optional<TracedSpan> oldSpan = older.span(spanName);

                // both configured
                if (newSpan.isPresent() && oldSpan.isPresent()) {
                    return Optional.of(TracedSpan.merge(oldSpan.get(), newSpan.get()));
                }

                // only newer
                if (newSpan.isPresent()) {
                    return newSpan;
                }

                return oldSpan;
            }

            @Override
            public Optional<Boolean> enabled() {
                return OptionalHelper.from(newer.enabled())
                        .or(older::enabled)
                        .asOptional();
            }
        };
    }

    /**
     * Get a traced span configuration for a named span.
     *
     * @param spanName name of the span in this component
     * @return configuration of that span if present
     */
    Optional<TracedSpan> span(String spanName);

    /**
     * Fluent API builder for traced component.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new traced component configuration from {@link Config}.
     *
     * @param config config for a new component
     * @return a new traced component configuration
     */
    static TracedComponent create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.tracing.config.TracedComponent}.
     */
    final class Builder implements io.helidon.common.Builder<TracedComponent> {
        private final Map<String, TracedSpan> tracedSpans = new HashMap<>();
        private Optional<Boolean> enabled = Optional.empty();

        private Builder() {
        }

        @Override
        public TracedComponent build() {
            // immutability
            final Optional<Boolean> finalEnabled = enabled;
            final Map<String, TracedSpan> finalSpans = new HashMap<>(tracedSpans);
            return new TracedComponent() {
                @Override
                public Optional<TracedSpan> span(String spanName) {
                    if (enabled.orElse(true)) {
                        return Optional.ofNullable(finalSpans.get(spanName));
                    } else {
                        return TracedConfigUtil.DISABLED_SPAN;
                    }
                }

                @Override
                public Optional<Boolean> enabled() {
                    return finalEnabled;
                }
            };
        }

        /**
         * Update this builder from {@link io.helidon.config.Config}.
         *
         * @param config configuration of a traced component
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("spans").asNodeList().ifPresent(spanConfigList -> {
                spanConfigList.forEach(spanConfig -> {
                    // span name is mandatory
                    addSpan(spanConfig.get("name").asString().get(), TracedSpan.create(spanConfig));
                });
            });
            return this;
        }

        /**
         * Add a new traced span configuration.
         *
         * @param spanName name of the new span
         * @param span configuration of a traced span
         * @return updated builder instance
         */
        public Builder addSpan(String spanName, TracedSpan span) {
            this.tracedSpans.put(spanName, span);
            return this;
        }

        /**
         * Configure whether this component is enabled or disabled.
         *
         * @param enabled if disabled, all spans and logs will be disabled
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }
    }
}
