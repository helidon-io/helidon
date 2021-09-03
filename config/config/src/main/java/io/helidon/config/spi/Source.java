/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Source of data.
 *
 * The actual loading of the data depends on the type of the source.
 *
 * @see io.helidon.config.spi.ParsableSource
 * @see io.helidon.config.spi.NodeConfigSource
 * @see LazyConfigSource
 */
public interface Source {
    /**
     * Short, human-readable summary referring to the underlying source.
     * <p>
     * For example, a file path or a URL or any other information that helps the
     * user recognize the underlying origin of the data this {@code Source}
     * provides.
     * <p>
     * Default is the implementation class simple name with any {@code "Source"}
     * suffix removed.
     *
     * @return description of the source
     */
    default String description() {
        String name = this.getClass().getSimpleName();
        if (name.endsWith("Source")) {
            name = name.substring(0, name.length() - "Source".length());
        }
        return name;
    }

    /**
     * If the underlying data exist at this time.
     * This is to prevent us loading such a source if we know it does not exist.
     *
     * @return {@code true} if the source exists, {@code false} otherwise
     */
    default boolean exists() {
        return true;
    }

    /**
     * Retry policy configured on this config source.
     *
     * @return configured retry policy
     */
    default Optional<RetryPolicy> retryPolicy() {
        return Optional.empty();
    }

    /**
     * Whether this source is optional.
     *
     * @return return {@code true} for optional source, returns {@code false} by default
     */
    default boolean optional() {
        return false;
    }

    /**
     * Configurable options of a {@link io.helidon.config.spi.Source}.
     *
     * @param <B> type implementation class of this interface
     */
    interface Builder<B extends Builder<B>> {
        /**
         * Configure a retry policy to be used with this source.
         * If none is configured, the source is invoked directly with no retries.
         *
         * @param policy retry policy to use
         * @return updated builder instance
         */
        B retryPolicy(Supplier<? extends RetryPolicy> policy);

        /**
         * Whether the source is optional or not.
         * When configured to be optional, missing underlying data do not cause an exception to be raised.
         *
         * @param optional {@code true} when this source should be optional
         * @return updated builder instance
         */
        B optional(boolean optional);

        /**
         * Configure this source to be optional.
         * <p>
         * Same as calling {@link #optional(boolean) optional(true)}.
         *
         * @return updated builder instance
         */
        default B optional() {
            return optional(true);
        }
    }
}
