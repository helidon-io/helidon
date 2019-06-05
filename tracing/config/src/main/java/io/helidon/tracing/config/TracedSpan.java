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
 * Configuration of a single traced span.
 */
public interface TracedSpan extends Traced {
    /**
     * A traced span that is disabled and all logs on it are disabled as well.
     */
    TracedSpan DISABLED = TracedSpan.builder().enabled(false).build();
    /**
     * A traced span that is inabled and all logs on it are enabled as well.
     */
    TracedSpan ENABLED = TracedSpan.builder().build();

    /**
     * Merge configuration of two traced spans.
     *
     * @param older older span with default values
     * @param newer newer span overriding values in older
     * @return a new merged traced span configuration
     */
    static TracedSpan merge(TracedSpan older, TracedSpan newer) {
        return new TracedSpan() {
            @Override
            public Optional<String> newName() {
                return OptionalHelper.from(newer.newName())
                        .or(older::newName)
                        .asOptional();
            }

            @Override
            public Optional<Boolean> enabled() {
                return OptionalHelper.from(newer.enabled())
                        .or(older::enabled)
                        .asOptional();
            }

            @Override
            public Optional<TracedSpanLog> spanLog(String name) {
                Optional<TracedSpanLog> newLog = newer.spanLog(name);
                Optional<TracedSpanLog> oldLog = older.spanLog(name);

                if (newLog.isPresent() && oldLog.isPresent()) {
                    return Optional.of(TracedSpanLog.merge(oldLog.get(), newLog.get()));
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
    Optional<String> newName();

    /**
     * Configuration of a traced span log.
     *
     * @param name name of the log event
     * @return configuration of the log event
     */
    Optional<TracedSpanLog> spanLog(String name);

    /**
     * Whether a log event should be logged on the span.
     *
     * @param logName name of the log event
     * @return whether to log ({@code true}) the event or not ({@code false}), defaults to {@code true} for unconfigured logs
     */
    default boolean logEnabled(String logName) {
        return spanLog(logName).flatMap(Traced::enabled).orElse(true);
    }

    /**
     * Whether a log event should be logged on the span with a default value.
     *
     * @param logName name of the log event
     * @param defaultValue to use in case the log event is not configured in this span's configuration
     * @return whether to log ({@code true}) the event or not ({@code false}), uses the default value for unconfigured logs
     */
    default boolean logEnabled(String logName, boolean defaultValue) {
        return spanLog(logName).flatMap(Traced::enabled).orElse(defaultValue);
    }

    /**
     * A fluent API builder to create traced span configuration.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create traced span configuration from a {@link io.helidon.config.Config}.
     *
     * @param config config to load span configuration from
     * @return a new traced span configuration
     */
    static TracedSpan create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A fluent API builder for {@link io.helidon.tracing.config.TracedSpan}.
     */
    final class Builder implements io.helidon.common.Builder<TracedSpan> {
        private final Map<String, TracedSpanLog> spanLogMap = new HashMap<>();
        private Optional<Boolean> enabled = Optional.empty();
        private String newName;

        private Builder() {
        }

        @Override
        public TracedSpan build() {
            final Map<String, TracedSpanLog> finalSpanLogMap = new HashMap<>(spanLogMap);
            final Optional<String> finalNewName = Optional.ofNullable(newName);
            final Optional<Boolean> finalEnabled = enabled;
            return new TracedSpan() {
                @Override
                public Optional<String> newName() {
                    return finalNewName;
                }

                @Override
                public Optional<Boolean> enabled() {
                    return finalEnabled;
                }

                @Override
                public Optional<TracedSpanLog> spanLog(String name) {
                    if (enabled.orElse(true)) {
                        return Optional.ofNullable(finalSpanLogMap.get(name));
                    }
                    return Optional.of(TracedSpanLog.DISABLED);
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
         * @param name name of the log event used in this span
         * @param tracedSpanLog configuration of the traced span log
         * @return updated builder instance
         */
        public Builder addSpanLog(String name, TracedSpanLog tracedSpanLog) {
            this.spanLogMap.put(name, tracedSpanLog);
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
                            addSpanLog(node.get("name").asString().get(), TracedSpanLog.create(node));
                        });
                    });

            return this;
        }
    }

}
