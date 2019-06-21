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
public abstract class EnvTracingConfig extends Traceable {
    /**
     * Traced config that is enabled for all components, spans and logs.
     */
    public static final EnvTracingConfig ENABLED = EnvTracingConfig.builder().build();
    /**
     * Traced conifg that is disabled for all components, spans and logs.
     */
    public static final EnvTracingConfig DISABLED = EnvTracingConfig.builder().enabled(false).build();

    /**
     * A new traced configuration.
     *
     * @param name name of this environment, when created using {@link EnvTracingConfig.Builder},
     *             the name is {@code helidon}
     */
    protected EnvTracingConfig(String name) {
        super(name);
    }

    /**
     * Configuration of a traced component.
     *
     * @param componentName name of the component
     * @return component tracing configuration or empty if defaults should be used
     */
    protected abstract Optional<ComponentTracingConfig> getComponent(String componentName);

    /**
     * Configuration of a traced component.
     *
     * @param componentName name of the component
     * @return component tracing configuration if configured, or an enabled component configuration
     */
    public ComponentTracingConfig component(String componentName) {
        return component(componentName, true);
    }

    /**
     * Configuration of a traced component.
     *
     * @param componentName name of the component
     * @param enabledByDefault whether the component should be enabled or disabled in case it is not configured
     * @return component tracing configuration if configured, or an enabled/disabled component configuration depending on
     *          {@code enabledByDefault}
     */
    public ComponentTracingConfig component(String componentName, boolean enabledByDefault) {
        if (enabled()) {
            return getComponent(componentName)
                    .orElseGet(() -> enabledByDefault ? ComponentTracingConfig.ENABLED : ComponentTracingConfig.DISABLED);
        }

        return ComponentTracingConfig.DISABLED;
    }

    /**
     * Create new tracing configuration based on the provided config.
     *
     * @param config configuration of tracing
     * @return tracing configuration
     */
    public static EnvTracingConfig create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A fluent API builder for tracing configuration.
     * @return a new builder instance
     */
    public static Builder builder() {
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
    public static EnvTracingConfig merge(EnvTracingConfig older, EnvTracingConfig newer) {
        return new EnvTracingConfig(newer.name()) {
            @Override
            public Optional<ComponentTracingConfig> getComponent(String componentName) {
                Optional<ComponentTracingConfig> newerComponent = newer.getComponent(componentName);
                Optional<ComponentTracingConfig> olderComponent = older.getComponent(componentName);

                // both configured
                if (newerComponent.isPresent() && olderComponent.isPresent()) {
                    return Optional.of(ComponentTracingConfig.merge(olderComponent.get(), newerComponent.get()));
                }

                // only newer configured
                if (newerComponent.isPresent()) {
                    return newerComponent;
                }

                // only older configured
                return olderComponent;
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
     * Return configuration of a specific span.
     * This is a shortcut method to {@link #component(String)} and
     * {@link ComponentTracingConfig#span(String)}.
     *
     * @param component component, such as "web-server", "security"
     * @param spanName name of the span, such as "HTTP Request", "security:atn"
     * @return configuration of the span if present in this traced system configuration
     */
    public SpanTracingConfig spanConfig(String component, String spanName) {
        return component(component).span(spanName);
    }

    /**
     * Fluent API builder for {@link EnvTracingConfig}.
     */
    public static final class Builder implements io.helidon.common.Builder<EnvTracingConfig> {
        private final Map<String, ComponentTracingConfig> components = new HashMap<>();
        private Optional<Boolean> enabled = Optional.empty();

        private Builder() {
        }

        @Override
        public EnvTracingConfig build() {
            // immutability
            final Map<String, ComponentTracingConfig> finalComponents = new HashMap<>(components);
            final Optional<Boolean> finalEnabled = enabled;

            return new EnvTracingConfig("helidon") {
                @Override
                public Optional<ComponentTracingConfig> getComponent(String componentName) {
                    return Optional.ofNullable(finalComponents.get(componentName));
                }

                @Override
                public Optional<Boolean> isEnabled() {
                    return finalEnabled;
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
                        compList.forEach(componentConfig -> addComponent(ComponentTracingConfig.create(componentConfig.name(),
                                                                                                       componentConfig)));
                    });

            return this;
        }

        /**
         * Add a traced component configuration.
         *
         * @param component configuration of this component's tracing
         * @return updated builder instance
         */
        public Builder addComponent(ComponentTracingConfig component) {
            components.put(component.name(), component);
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
