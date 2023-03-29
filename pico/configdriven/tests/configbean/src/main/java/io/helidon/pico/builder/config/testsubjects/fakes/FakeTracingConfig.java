/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.testsubjects.fakes;

import java.util.Map;

import io.helidon.builder.Singular;
import io.helidon.pico.builder.config.ConfigBean;

/**
 * aka TracingConfig.
 *
 * Tracing configuration that contains traced components (such as WebServer, Security) and their traced spans and span logs.
 * Spans can be renamed through configuration, components, spans and span logs may be disabled through this configuration.
 */
@ConfigBean(key = "tracing")
public interface FakeTracingConfig extends FakeTraceableConfig {

//    /**
//     * Traced config that is enabled for all components, spans and logs.
//     */
//    FakeTracingConfig ENABLED = FakeTracingConfig.builder().build();
//    /**
//     * Traced conifg that is disabled for all components, spans and logs.
//     */
//    FakeTracingConfig DISABLED = FakeTracingConfig.builder().enabled(false).build();

//    /**
//     * A new traced configuration.
//     *
//     * @param name name of this configuration, when created using {@link FakeTracingConfig.Builder},
//     *             the name is {@code helidon}
//     */
//    protected FakeTracingConfig(String name) {
//        super(name);
//    }
//
//    /**
//     * Configuration of a traced component.
//     *
//     * @param componentName name of the component
//     * @return component tracing configuration or empty if defaults should be used
//     */
//    protected abstract Optional<ComponentTracingConfig> getComponent(String componentName);
//
//    /**
//     * Configuration of a traced component.
//     *
//     * @param componentName name of the component
//     * @return component tracing configuration if configured, or an enabled component configuration
//     */
//    public ComponentTracingConfig component(String componentName) {
//        return component(componentName, true);
//    }

    @Singular("component")  // Builder::addComponent(String component); Impl::getComponent(String component);
    Map<String, FakeComponentTracingConfig> components();

//    /**
//     * Configuration of a traced component.
//     *
//     * @param componentName name of the component
//     * @param enabledByDefault whether the component should be enabled or disabled in case it is not configured
//     * @return component tracing configuration if configured, or an enabled/disabled component configuration depending on
//     *          {@code enabledByDefault}
//     */
//    public ComponentTracingConfig component(String componentName, boolean enabledByDefault) {
//        if (enabled()) {
//            return getComponent(componentName)
//                    .orElseGet(() -> enabledByDefault ? ComponentTracingConfig.ENABLED : ComponentTracingConfig.DISABLED);
//        }
//
//        return ComponentTracingConfig.DISABLED;
//    }
//
//    @Override
//    public String toString() {
//        return "TracingConfig(" + name() + ")";
//    }
//
//    /**
//     * Create new tracing configuration based on the provided config.
//     *
//     * @param config configuration of tracing
//     * @return tracing configuration
//     */
//    public static FakeTracingConfig create(Config config) {
//        return builder().config(config).build();
//    }
//
//    /**
//     * A fluent API builder for tracing configuration.
//     * @return a new builder instance
//     */
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    /**
//     * Merge two configurations together.
//     * The result will combine configuration from both configurations. In case
//     * of conflicts, the {@code newer} wins.
//     *
//     * @param older older instance to merge
//     * @param newer newer (more significant) instance to merge
//     * @return a new configuration combining odler and newer
//     */
//    public static FakeTracingConfig merge(FakeTracingConfig older, FakeTracingConfig newer) {
//        return new FakeTracingConfig(newer.name()) {
//            @Override
//            public Optional<ComponentTracingConfig> getComponent(String componentName) {
//                Optional<ComponentTracingConfig> newerComponent = newer.getComponent(componentName);
//                Optional<ComponentTracingConfig> olderComponent = older.getComponent(componentName);
//
//                // both configured
//                if (newerComponent.isPresent() && olderComponent.isPresent()) {
//                    return Optional.of(ComponentTracingConfig.merge(olderComponent.get(), newerComponent.get()));
//                }
//
//                // only newer configured
//                if (newerComponent.isPresent()) {
//                    return newerComponent;
//                }
//
//                // only older configured
//                return olderComponent;
//            }
//
//            @Override
//            public Optional<Boolean> isEnabled() {
//                return newer.isEnabled()
//                        .or(older::isEnabled);
//            }
//        };
//    }
//
//    /**
//     * Return configuration of a specific span.
//     * This is a shortcut method to {@link #component(String)} and
//     * {@link ComponentTracingConfig#span(String)}.
//     *
//     * @param component component, such as "web-server", "security"
//     * @param spanName name of the span, such as "HTTP Request", "security:atn"
//     * @return configuration of the span if present in this traced system configuration
//     */
//    public SpanTracingConfig spanConfig(String component, String spanName) {
//        return component(component).span(spanName);
//    }

//    /**
//     * Fluent API builder for {@link FakeTracingConfig}.
//     */
//    public static final class Builder implements io.helidon.common.Builder<Builder, FakeTracingConfig> {
//        private final Map<String, ComponentTracingConfig> components = new HashMap<>();
//        private Optional<Boolean> enabled = Optional.empty();
//
//        private Builder() {
//        }
//
//        @Override
//        public FakeTracingConfig build() {
//            return new RootTracingConfig("helidon", new HashMap<>(components), enabled);
//        }
//
//        /**
//         * Update this builder from configuration.
//         *
//         * @param config Config with tracing configuration
//         * @return updated builder instance
//         */
//        public Builder config(Config config) {
//            config.get("enabled").asBoolean().ifPresent(this::enabled);
//            Config compConfig = config.get("components");
//            compConfig.asNodeList()
//                    .ifPresent(compList -> {
//                        compList.forEach(componentConfig -> addComponent(ComponentTracingConfig.create(componentConfig.name(),
//                                                                                                       componentConfig)));
//                    });
//
//            return this;
//        }
//
//        /**
//         * Add a traced component configuration.
//         *
//         * @param component configuration of this component's tracing
//         * @return updated builder instance
//         */
//        public Builder addComponent(ComponentTracingConfig component) {
//            components.put(component.name(), component);
//            return this;
//        }
//
//        /**
//         * Whether overall tracing is enabled.
//         * If tracing is disabled on this level, all traced components and spans are disabled - even if explicitly configured
//         *  as enabled.
//         *
//         * @param enabled set to {@code false} to disable tracing for any component and span
//         * @return updated builder instance
//         */
//        public Builder enabled(boolean enabled) {
//            this.enabled = Optional.of(enabled);
//            return this;
//        }
//    }
//
//    static final class RootTracingConfig extends FakeTracingConfig {
//        private final Map<String, ComponentTracingConfig> components;
//        private final Optional<Boolean> enabled;
//
//        RootTracingConfig(String name,
//                          Map<String, ComponentTracingConfig> components,
//                          Optional<Boolean> enabled) {
//            super(name);
//            this.components = components;
//            this.enabled = enabled;
//        }
//
//        @Override
//        public Optional<ComponentTracingConfig> getComponent(String componentName) {
//            return Optional.ofNullable(components.get(componentName));
//        }
//
//        @Override
//        public Optional<Boolean> isEnabled() {
//            return enabled;
//        }
//
//    }
}
