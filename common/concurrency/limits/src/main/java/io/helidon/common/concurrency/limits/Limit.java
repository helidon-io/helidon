/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.config.NamedService;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Service;

/**
 * Contract for a concurrency limiter.
 */
@Service.Contract
public interface Limit extends LimitAlgorithm, NamedService {
    /**
     * Create a copy of this limit with the same configuration.
     *
     * @return a copy of this limit
     */
    Limit copy();

    /**
     * Initialization method for this limit. This method can be used for any
     * task, including metrics initialization.
     *
     * @param context the context for limit initialization
     */
    default void init(InitializationContext context) {
        init(context.originName());
    }

    /**
     * Initialization method for this limit. This method can be used for any
     * task, including metrics initialization.
     *
     * @param originName origin name for this limit, such as {@code "@default"}
     * @deprecated use {@link #init(InitializationContext)} to provide initialization details explicitly.
     */
    @Deprecated
    default void init(String originName) {
    }

    /**
     * Runtime context used when initializing a {@link Limit}.
     */
    final class InitializationContext {
        private final String originName;
        private final List<Tag> metricTags;

        private InitializationContext(String originName, List<Tag> metricTags) {
            this.originName = Objects.requireNonNull(originName);
            this.metricTags = List.copyOf(metricTags);
        }

        /**
         * Create a limit context with no metric tags.
         *
         * @param originName origin name of the work protected by the limit
         * @return a new limit context
         */
        public static InitializationContext create(String originName) {
            return new InitializationContext(originName, List.of());
        }

        /**
         * Create a limit context with metric tags.
         *
         * @param originName origin name of the work protected by the limit
         * @param metricTags metric tags to use when registering limit metrics
         * @return a new limit context
         */
        public static InitializationContext create(String originName, List<Tag> metricTags) {
            return new InitializationContext(originName, metricTags);
        }

        /**
         * Create a limit context with metric tags.
         *
         * @param originName origin name of the work protected by the limit
         * @param metricTags metric tag names and values to use when registering limit metrics
         * @return a new limit context
         */
        public static InitializationContext create(String originName, Map<String, String> metricTags) {
            return new InitializationContext(originName,
                                             metricTags.entrySet()
                                                     .stream()
                                                     .map(it -> Tag.create(it.getKey(), it.getValue()))
                                                     .toList());
        }

        /**
         * Create a limit context preserving the legacy WebServer socket-name metric tag behavior.
         * The {@code socketName} tag is omitted for the default socket.
         *
         * @param socketName WebServer socket name
         * @return a new limit context
         */
        public static InitializationContext createForLegacySocketName(String socketName) {
            if (socketName.equals(Service.Named.DEFAULT_NAME)) {
                return create(socketName);
            }
            return create(socketName, Map.of("socketName", socketName));
        }

        /**
         * Origin name of the work protected by the limit.
         *
         * @return origin name
         */
        public String originName() {
            return originName;
        }

        /**
         * Metric tags to use when registering limit metrics.
         *
         * @return metric tags
         */
        public List<Tag> metricTags() {
            return metricTags;
        }
    }
}
