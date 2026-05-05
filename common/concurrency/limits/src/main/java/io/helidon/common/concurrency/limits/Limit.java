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
     */
    default void init(String originName) {
    }

    /**
     * Runtime context used when initializing a {@link Limit}.
     */
    interface InitializationContext {
        /**
         * Create a limit context with no metric tags.
         *
         * @param originName origin name of the work protected by the limit
         * @return a new limit context
         */
        static InitializationContext create(String originName) {
            return createContext(originName, List.of());
        }

        /**
         * Create a limit context with metric tags.
         *
         * @param originName origin name of the work protected by the limit
         * @param metricTags metric tags to use when registering limit metrics
         * @return a new limit context
         */
        static InitializationContext create(String originName, List<Tag> metricTags) {
            return createContext(originName, metricTags);
        }

        /**
         * Origin name of the work protected by the limit.
         *
         * @return origin name
         */
        String originName();

        /**
         * Metric tags to use when registering limit metrics.
         *
         * @return metric tags
         */
        List<Tag> metricTags();

        private static InitializationContext createContext(String originName, List<Tag> metricTags) {
            Objects.requireNonNull(originName);
            List<Tag> copiedTags = List.copyOf(metricTags);

            return new InitializationContext() {
                @Override
                public String originName() {
                    return originName;
                }

                @Override
                public List<Tag> metricTags() {
                    return copiedTags;
                }
            };
        }
    }
}
