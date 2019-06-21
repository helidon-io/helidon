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
 *     <li>web-server: webServer adds the root tracing span + two additional spans (content-read and content-write)</li>
 *     <li>security: security adds the overall request security span, a span for authentication ("security:atn"), a span for
 *          authorization "security:atz", and a span for response processing ("security:response")</li>
 *     <li>jax-rs: JAX-RS integration adds spans for overall resource invocation</li>
 * </ul>
 */
public abstract class ComponentTracingConfig extends Traceable {
    /**
     * Disabled component - all subsequent calls return disabled spans and logs.
     */
    public static final ComponentTracingConfig DISABLED = ComponentTracingConfig.builder("disabled").enabled(false).build();
    /**
     * Enabled component - all subsequent calls return enabled spans and logs.
     */
    public static final ComponentTracingConfig ENABLED = ComponentTracingConfig.builder("enabled").build();

    /**
     * A new named component.
     *
     * @param name name of the component
     */
    protected ComponentTracingConfig(String name) {
        super(name);
    }

    /**
     * Merge configuration of two traced components. This enabled hierarchical configuration
     * with common, default configuration in one traced component and override in another.
     *
     * @param older the older configuration with "defaults"
     * @param newer the newer configuration to override defaults in older
     * @return merged component
     */
    static ComponentTracingConfig merge(ComponentTracingConfig older, ComponentTracingConfig newer) {
        return new ComponentTracingConfig(newer.name()) {
            @Override
            public Optional<SpanTracingConfig> getSpan(String spanName) {
                if (!enabled()) {
                    return Optional.of(SpanTracingConfig.DISABLED);
                }

                Optional<SpanTracingConfig> newSpan = newer.getSpan(spanName);
                Optional<SpanTracingConfig> oldSpan = older.getSpan(spanName);

                // both configured
                if (newSpan.isPresent() && oldSpan.isPresent()) {
                    return Optional.of(SpanTracingConfig.merge(oldSpan.get(), newSpan.get()));
                }

                // only newer
                if (newSpan.isPresent()) {
                    return newSpan;
                }

                return oldSpan;
            }

            @Override
            public Optional<Boolean> isEnabled() {
                return OptionalHelper.from(newer.isEnabled())
                        .or(older::isEnabled)
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
    protected abstract Optional<SpanTracingConfig> getSpan(String spanName);

    /**
     * Get a traced span configuration for a named span.
     *
     * @param spanName name of a span in this component
     * @return configuration of the span, or enabled configuration if not configured
     * @see #span(String, boolean)
     */
    public SpanTracingConfig span(String spanName) {
        return span(spanName, true);
    }

    /**
     * Get a traced span configuration for a named span.
     *
     * @param spanName name of a span in this component
     * @param enabledByDefault whether the result is enabled if a configuration is not present
     * @return configuration of the span, or a span configuration enabled or disabled depending on {@code enabledByDefault} if
     * not configured
     */
    public SpanTracingConfig span(String spanName, boolean enabledByDefault) {
        if (enabled()) {
            return getSpan(spanName).orElseGet(() -> enabledByDefault ? SpanTracingConfig.ENABLED : SpanTracingConfig.DISABLED);
        }

        return SpanTracingConfig.DISABLED;
    }

    /**
     * Fluent API builder for traced component.
     *
     * @param name the name of the component
     * @return a new builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create a new traced component configuration from {@link Config}.
     *
     * @param name name of the component
     * @param config config for a new component
     * @return a new traced component configuration
     */
    public static ComponentTracingConfig create(String name, Config config) {
        return builder(name)
                .config(config)
                .build();
    }

    /**
     * Fluent API builder for {@link ComponentTracingConfig}.
     */
    public static final class Builder implements io.helidon.common.Builder<ComponentTracingConfig> {
        private final Map<String, SpanTracingConfig> tracedSpans = new HashMap<>();
        private Optional<Boolean> enabled = Optional.empty();
        private final String name;

        private Builder(String name) {
            this.name = name;
        }

        @Override
        public ComponentTracingConfig build() {
            // immutability
            final Optional<Boolean> finalEnabled = enabled;
            final Map<String, SpanTracingConfig> finalSpans = new HashMap<>(tracedSpans);
            return new ComponentTracingConfig(name) {
                @Override
                public Optional<SpanTracingConfig> getSpan(String spanName) {
                    if (enabled.orElse(true)) {
                        return Optional.ofNullable(finalSpans.get(spanName));
                    } else {
                        return Optional.of(SpanTracingConfig.DISABLED);
                    }
                }

                @Override
                public Optional<Boolean> isEnabled() {
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
                    addSpan(SpanTracingConfig.create(spanConfig.get("name").asString().get(), spanConfig));
                });
            });
            return this;
        }

        /**
         * Add a new traced span configuration.
         *
         * @param span configuration of a traced span
         * @return updated builder instance
         */
        public Builder addSpan(SpanTracingConfig span) {
            this.tracedSpans.put(span.name(), span);
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
