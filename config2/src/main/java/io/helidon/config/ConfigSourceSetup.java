/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;

/**
 * A configuration of a config source, handling common configuration around config sources.
 */
public class ConfigSourceSetup {
    private final ConfigSource configSource;
    private final PollingStrategy pollingStrategy;
    private final RetryPolicy retryPolicy;
    private final ChangeWatcher<?> changeWatcher;
    private final boolean optional;
    private final ConfigParser parser;
    private final String mediaType;

    public ConfigSourceSetup(Builder builder) {
        this.configSource = builder.configSource;
        this.pollingStrategy = builder.pollingStrategy;
        this.retryPolicy = builder.retryPolicy;
        this.changeWatcher = builder.changeWatcher;
        this.optional = builder.optional;
        this.parser = builder.parser;
        this.mediaType = builder.mediaType;
    }

    public ConfigSource configSource() {
        return configSource;
    }

    public boolean optional() {
        return optional;
    }

    public Optional<PollingStrategy> pollingStrategy() {
        return Optional.ofNullable(pollingStrategy);
    }

    public Optional<RetryPolicy> retryPolicy() {
        return Optional.ofNullable(retryPolicy);
    }

    public Optional<ConfigParser> parser() {
        return Optional.ofNullable(parser);
    }

    public Optional<String> mediaType() {
        return Optional.ofNullable(mediaType);
    }

    public static Builder builder(Supplier<ConfigSource> configSource) {
        return builder(configSource.get());
    }

    public static Builder builder(ConfigSource configSource) {
        return new Builder(configSource);
    }

    public static ConfigSourceSetup create(ConfigSource configSource) {
        return builder(configSource).build();
    }

    public static class Builder implements io.helidon.common.Builder<ConfigSourceSetup> {
        private final ConfigSource configSource;
        private PollingStrategy pollingStrategy;
        private RetryPolicy retryPolicy;
        private ChangeWatcher<?> changeWatcher;
        private boolean optional = false;
        private ConfigParser parser;
        private String mediaType;

        private Builder(ConfigSource configSource) {
            this.configSource = configSource;
        }

        @Override
        public ConfigSourceSetup build() {
            return new ConfigSourceSetup(this);
        }

        public Builder pollingStrategy(PollingStrategy pollingStrategy) {
            // make sure polling strategy is supported
            if (configSource instanceof ConfigSource.PollableSource) {
                this.pollingStrategy = pollingStrategy;
                return this;
            }

            throw new ConfigException("Polling strategy cannot be configured on a config source that cannot be polled. Class: "
                                              + configSource.getClass().getName()
                                              + ", toString: "
                                              + configSource);
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder changeWatcher(ChangeWatcher<?> changeWatcher) {
            if (configSource instanceof ConfigSource.WatchableSource) {
                // make sure the change watcher supports config source target
                Class<?> changeWatcherType = changeWatcher.type();
                Class<?> configSourceType = ((ConfigSource.WatchableSource<?>) configSource).targetType();
                if (changeWatcherType.isAssignableFrom(configSourceType)) {
                    this.changeWatcher = changeWatcher;
                    return this;
                }
                throw new ConfigException(
                        "Change watcher " + changeWatcher + " supports targets of type " + changeWatcherType.getName()
                                + ", config source supports "
                                + configSourceType.getName()
                                + ", these are not compatible. Class: "
                                + configSource.getClass().getName()
                                + ", toString: "
                                + configSource);
            }

            throw new ConfigException(
                    "Change watcher cannot be configured on a config source that does not have a target. Class: "
                            + configSource.getClass().getName()
                            + ", toString: "
                            + configSource);
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder parser(ConfigParser parser) {
            if (configSource instanceof ConfigSource.ParsableSource) {
                this.parser = parser;
                return this;
            }

            throw new ConfigException(
                    "Config parser cannot be configured on a config source that is not parsable. Class: "
                            + configSource.getClass().getName()
                            + ", toString: "
                            + configSource);
        }

        public Builder mediaType(String mediaType) {
            if (configSource instanceof ConfigSource.ParsableSource) {
                this.mediaType = mediaType;
                return this;
            }

            throw new ConfigException(
                    "Media type cannot be configured on a config source that is not parsable. Class: "
                            + configSource.getClass().getName()
                            + ", toString: "
                            + configSource);
        }
    }
}
