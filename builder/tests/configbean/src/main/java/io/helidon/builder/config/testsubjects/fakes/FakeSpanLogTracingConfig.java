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

package io.helidon.builder.config.testsubjects.fakes;

import io.helidon.builder.config.ConfigBean;

/**
 * aka SpanLogTracingConfig.
 * Configuration of a single log event in a traced span.
 */
@ConfigBean
public interface FakeSpanLogTracingConfig extends FakeTraceableConfig {
//    /**
//     * Disabled traced span log.
//     */
//    public static final FakeSpanLogTracingConfigBean DISABLED = FakeSpanLogTracingConfigBean.builder("disabled").enabled(false).build();
//    /**
//     * Enabled traced span log.
//     */
//    public static final FakeSpanLogTracingConfigBean ENABLED = FakeSpanLogTracingConfigBean.builder("enabled").build();
//
//    /**
//     * A new span log.
//     * @param name name of the span log
//     */
//    protected FakeSpanLogTracingConfigBean(String name) {
//        super(name);
//    }
//

//    /**
//     * Merge two traced span log configurations.
//     *
//     * @param older original configuration with default values
//     * @param newer new configuration to override the older
//     * @return a new traced span log mergint the older and newer
//     */
//    static FakeSpanLogTracingConfigBean merge(FakeSpanLogTracingConfigBean older, FakeSpanLogTracingConfigBean newer) {
//        return new FakeSpanLogTracingConfigBean(newer.name()) {
//            @Override
//            public Optional<Boolean> isEnabled() {
//                return newer.isEnabled()
//                        .or(older::isEnabled);
//            }
//        };
//    }
//
//    /**
//     * Fluent API builder to create a new traced span log configuration.
//     *
//     * @param name name of the span log
//     * @return a new builder instance
//     */
//    public static Builder builder(String name) {
//        return new Builder(name);
//    }
//
//    /**
//     * Create a new traced span log configuration from {@link io.helidon.config.Config}.
//     *
//     * @param name name of the span log
//     * @param config config for a traced span log
//     * @return a new traced span log configuration
//     */
//    public static FakeSpanLogTracingConfigBean create(String name, Config config) {
//        return builder(name).config(config).build();
//    }
//
//    /**
//     * A fluent API builder for {@link FakeSpanLogTracingConfigBean}.
//     */
//    public static final class Builder implements io.helidon.common.Builder<Builder, FakeSpanLogTracingConfigBean> {
//        private final String name;
//        private Optional<Boolean> enabled = Optional.empty();
//
//        private Builder(String name) {
//            this.name = name;
//        }
//
//        @Override
//        public FakeSpanLogTracingConfigBean build() {
//            final Optional<Boolean> finalEnabled = enabled;
//            return new FakeSpanLogTracingConfigBean(name) {
//                @Override
//                public Optional<Boolean> isEnabled() {
//                    return finalEnabled;
//                }
//            };
//        }
//
//        /**
//         * Configure whether this traced span log is enabled or disabled.
//         *
//         * @param enabled if disabled, this span and all logs will be disabled
//         * @return updated builder instance
//         */
//        public Builder enabled(boolean enabled) {
//            this.enabled = Optional.of(enabled);
//            return this;
//        }
//
//        /**
//         * Update this builder from {@link io.helidon.config.Config}.
//         *
//         * @param config config of a traced span log
//         * @return updated builder instance
//         */
//        public Builder config(Config config) {
//            config.get("enabled").asBoolean().ifPresent(this::enabled);
//
//            return this;
//        }
//    }
//
}
