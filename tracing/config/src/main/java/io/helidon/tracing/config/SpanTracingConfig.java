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

import io.helidon.config.Config;

/**
 * Configuration of a single traced span.
 */
public abstract class SpanTracingConfig extends Traceable {
    /**
     * A traced span that is disabled and all logs on it are disabled as well.
     */
    public static final SpanTracingConfig DISABLED = SpanTracingConfig.builder("disabled").enabled(false).build();
    /**
     * A traced span that is inabled and all logs on it are enabled as well.
     */
    public static final SpanTracingConfig ENABLED = SpanTracingConfig.builder("enabled").build();

    /**
     * A new traceable span.
     *
     * @param name name of this span
     */
    protected SpanTracingConfig(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return "SpanTracingConfig(" + name() + ")";
    }

    /**
     * Merge configuration of two traced spans.
     *
     * @param older older span with default values
     * @param newer newer span overriding values in older
     * @return a new merged traced span configuration
     */
    static SpanTracingConfig merge(SpanTracingConfig older, SpanTracingConfig newer) {
        return new SpanTracingConfig(newer.name()) {
            @Override
            public Optional<String> newName() {
                return newer.newName()
                        .or(older::newName);
            }

            @Override
            public Optional<Boolean> isEnabled() {
                return newer.isEnabled()
                        .or(older::isEnabled);
            }

            @Override
            public Optional<SpanLogTracingConfig> getSpanLog(String name) {
                Optional<SpanLogTracingConfig> newLog = newer.getSpanLog(name);
                Optional<SpanLogTracingConfig> oldLog = older.getSpanLog(name);

                if (newLog.isPresent() && oldLog.isPresent()) {
                    return Optional.of(SpanLogTracingConfig.merge(oldLog.get(), newLog.get()));
                }

                if (newLog.isPresent()) {
                    return newLog;
                }

                return oldLog;
            }
        };
    }

    /**
     * When rename is desired, returns the new name.
     *
     * @return new name for this span or empty when rename is not desired
     */
    public abstract Optional<String> newName();

    /**
     * Configuration of a traced span log.
     *
     * @param name name of the log event
     * @return configuration of the log event, or empty if not explicitly configured (used when merging)
     */
    protected abstract Optional<SpanLogTracingConfig> getSpanLog(String name);

    /**
     * Configuration of a traceable span log.
     * If this span is disabled, the log is always disabled.
     *
     * @param name name of the log event
     * @return configuration of the log event
     */
    public final SpanLogTracingConfig spanLog(String name) {
        if (enabled()) {
            return getSpanLog(name).orElse(SpanLogTracingConfig.ENABLED);
        } else {
            return SpanLogTracingConfig.DISABLED;
        }
    }

    /**
     * Whether a log event should be logged on the span with a default value.
     *
     * @param logName name of the log event
     * @param defaultValue to use in case the log event is not configured in this span's configuration
     * @return whether to log ({@code true}) the event or not ({@code false}), uses the default value for unconfigured logs
     */
    public boolean logEnabled(String logName, boolean defaultValue) {
        if (enabled()) {
            return getSpanLog(logName).map(Traceable::enabled).orElse(defaultValue);
        }
        return false;
    }

    /**
     * A fluent API builder to create traced span configuration.
     *
     * @param name name of the span
     * @return a new builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Create traced span configuration from a {@link io.helidon.config.Config}.
     *
     * @param name name of the span
     * @param config config to load span configuration from
     * @return a new traced span configuration
     */
    public static SpanTracingConfig create(String name, Config config) {
        return builder(name).config(config).build();
    }

    /**
     * A fluent API builder for {@link SpanTracingConfig}.
     */
    public static final class Builder implements io.helidon.common.Builder<SpanTracingConfig> {
        private final Map<String, SpanLogTracingConfig> spanLogMap = new HashMap<>();
        private final String name;
        private Optional<Boolean> enabled = Optional.empty();
        private String newName;

        private Builder(String name) {
            this.name = name;
        }

        @Override
        public SpanTracingConfig build() {
            final Map<String, SpanLogTracingConfig> finalSpanLogMap = new HashMap<>(spanLogMap);
            final Optional<String> finalNewName = Optional.ofNullable(newName);
            final Optional<Boolean> finalEnabled = enabled;

            return new SpanTracingConfig(name) {
                @Override
                public Optional<String> newName() {
                    return finalNewName;
                }

                @Override
                public Optional<Boolean> isEnabled() {
                    return finalEnabled;
                }

                @Override
                protected Optional<SpanLogTracingConfig> getSpanLog(String name) {
                    if (enabled.orElse(true)) {
                        return Optional.ofNullable(finalSpanLogMap.get(name));
                    }
                    return Optional.of(SpanLogTracingConfig.DISABLED);
                }
            };
        }

        /**
         * Configure whether this traced span is enabled or disabled.
         *
         * @param enabled if disabled, this span and all logs will be disabled
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }

        /**
         * Configure a new name of this span.
         *
         * @param newName new name to use when reporting this span
         * @return updated builder instance
         */
        public Builder newName(String newName) {
            this.newName = newName;
            return this;
        }

        /**
         * Add configuration of a traced span log.
         *
         * @param spanLogTracingConfig configuration of the traced span log
         * @return updated builder instance
         */
        public Builder addSpanLog(SpanLogTracingConfig spanLogTracingConfig) {
            this.spanLogMap.put(spanLogTracingConfig.name(), spanLogTracingConfig);
            return this;
        }

        /**
         * Update this builder from {@link io.helidon.config.Config}.
         *
         * @param config configuration of this span
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("new-name").asString().ifPresent(this::newName);
            config.get("logs")
                    .asNodeList()
                    .ifPresent(nodes -> {
                        nodes.forEach(node -> {
                            // name is mandatory
                            addSpanLog(SpanLogTracingConfig.create(node.get("name").asString().get(), node));
                        });
                    });

            return this;
        }
    }

}
