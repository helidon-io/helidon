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
 * Configuration of traced environment.
 *
 * @see #create(io.helidon.config.Config)
 * @see #builder()
 */
public interface TracedConfig extends Traced {
    /**
     * Traced config that is enabled for all components, spans and logs.
     */
    TracedConfig ENABLED = TracedConfig.builder().build();
    /**
     * Traced conifg that is disabled for all components, spans and logs.
     */
    TracedConfig DISABLED = TracedConfig.builder().enabled(false).build();

    /**
     * Configuration of a traced component.
     *
     * @param componentName name of the component
     * @return component tracing configuration or empty if defaults should be used
     */
    Optional<TracedComponent> component(String componentName);

    /**
     * Create new tracing configuration based on the provided config.
     *
     * @param config configuration of tracing
     * @return tracing configuration
     */
    static TracedConfig create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A fluent API builder for tracing configuration.
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Merge two configurations together.
     * The result will combine configuration from both configurations. In case
     * of conflicts, the {@code newer} wins.
     *
     * @param older older instance to merge
     * @param newer newer (more significant) instance to merge
     * @return a new configuration combining odler and newer
     */
    static TracedConfig merge(TracedConfig older, TracedConfig newer) {
        return new TracedConfig() {
            @Override
            public Optional<TracedComponent> component(String componentName) {
                if (!enabled().orElse(true)) {
                    return TracedConfigUtil.DISABLED_COMPONENT;
                }
                Optional<TracedComponent> newerComponent = newer.component(componentName);
                Optional<TracedComponent> olderComponent = older.component(componentName);

                // both configured
                if (newerComponent.isPresent() && olderComponent.isPresent()) {
                    return Optional.of(TracedComponent.merge(olderComponent.get(), newerComponent.get()));
                }

                // only newer configured
                if (newerComponent.isPresent()) {
                    return newerComponent;
                }

                // only older configured
                return olderComponent;
            }

            @Override
            public Optional<TracedSpan> spanConfig(String componentName, String spanName) {
                Optional<TracedComponent> component = component(componentName);
                if (component.flatMap(TracedComponent::enabled).orElse(true)) {
                    return component.flatMap(trComp -> trComp.span(spanName));
                }
                return TracedConfigUtil.DISABLED_SPAN;
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
     * Return configuration of a specific span.
     *
     * @param component component, such as "web-server", "security"
     * @param spanName name of the span, such as "HTTP Request", "security:atn"
     * @return configuration of the span if present in this traced system configuration
     */
    Optional<TracedSpan> spanConfig(String component, String spanName);

    /**
     * Fluent API builder for {@link TracedConfig}.
     */
    final class Builder implements io.helidon.common.Builder<TracedConfig> {
        private final Map<String, TracedComponent> components = new HashMap<>();
        private Optional<Boolean> enabled = Optional.empty();

        private Builder() {
        }

        @Override
        public TracedConfig build() {
            // immutability
            final Map<String, TracedComponent> finalComponents = new HashMap<>(components);
            final Optional<Boolean> finalEnabled = enabled;

            return new TracedConfig() {
                @Override
                public Optional<TracedComponent> component(String componentName) {
                    if (enabled().orElse(true)) {
                        return Optional.ofNullable(finalComponents.get(componentName));
                    } else {
                        return TracedConfigUtil.DISABLED_COMPONENT;
                    }
                }

                @Override
                public Optional<Boolean> enabled() {
                    return finalEnabled;
                }

                @Override
                public Optional<TracedSpan> spanConfig(String component, String spanName) {
                    if (enabled().orElse(true)) {
                        TracedComponent tracedComponent = component(component).orElse(TracedComponent.ENABLED);
                        if (tracedComponent.enabled().orElse(true)) {
                            return tracedComponent.span(spanName);
                        }
                    }

                    // disabled
                    return TracedConfigUtil.DISABLED_SPAN;
                }
            };
        }

        /**
         * Update this builder from configuration.
         *
         * @param config Config with tracing configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            Config compConfig = config.get("components");
            compConfig.asNodeList()
                    .ifPresent(compList -> {
                        compList.forEach(componentConfig -> addComponent(componentConfig.name(),
                                                                         TracedComponent.create(componentConfig)));
                    });

            return this;
        }

        /**
         * Add a traced component configuration.
         *
         * @param componentName name of the component
         * @param component configuration of this component's tracing
         * @return updated builder instance
         */
        public Builder addComponent(String componentName, TracedComponent component) {
            components.put(componentName, component);
            return this;
        }

        /**
         * Whether overall tracing is enabled.
         * If tracing is disabled on this level, all traced components and spans are disabled - even if explicitly configured
         *  as enabled.
         *
         * @param enabled set to {@code false} to disable tracing for any component and span
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }
    }
}
