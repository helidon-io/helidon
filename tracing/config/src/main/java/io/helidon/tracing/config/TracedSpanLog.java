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

import java.util.Optional;

import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;

/**
 * Configuration of a single log event in a traced span.
 */
public interface TracedSpanLog extends Traced {
    /**
     * Disabled traced span log.
     */
    TracedSpanLog DISABLED = TracedSpanLog.builder().enabled(false).build();
    /**
     * Enabled traced span log.
     */
    TracedSpanLog ENABLED = TracedSpanLog.builder().build();

    /**
     * Merge two traced span log configurations.
     *
     * @param older original configuration with default values
     * @param newer new configuration to override the older
     * @return a new traced span log mergint the older and newer
     */
    static TracedSpanLog merge(TracedSpanLog older, TracedSpanLog newer) {
        return new TracedSpanLog() {
            @Override
            public Optional<Boolean> enabled() {
                return OptionalHelper.from(newer.enabled())
                        .or(older::enabled)
                        .asOptional();
            }
        };
    }

    /**
     * Fluent API builder to create a new traced span log configuration.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new traced span log configuration from {@link io.helidon.config.Config}.
     *
     * @param config config for a traced span log
     * @return a new traced span log configuration
     */
    static TracedSpanLog create(Config config) {
        return builder().config(config).build();
    }

    /**
     * A fluent API builder for {@link io.helidon.tracing.config.TracedSpanLog}.
     */
    final class Builder implements io.helidon.common.Builder<TracedSpanLog> {
        private Optional<Boolean> enabled = Optional.empty();

        private Builder() {
        }

        @Override
        public TracedSpanLog build() {
            final Optional<Boolean> finalEnabled = enabled;
            return new TracedSpanLog() {
                @Override
                public Optional<Boolean> enabled() {
                    return finalEnabled;
                }
            };
        }

        /**
         * Configure whether this traced span log is enabled or disabled.
         *
         * @param enabled if disabled, this span and all logs will be disabled
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = Optional.of(enabled);
            return this;
        }

        /**
         * Update this builder from {@link io.helidon.config.Config}.
         *
         * @param config config of a traced span log
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("enabled").asBoolean().ifPresent(this::enabled);

            return this;
        }
    }

}
