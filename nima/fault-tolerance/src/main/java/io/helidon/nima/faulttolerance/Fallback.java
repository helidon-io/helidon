/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.faulttolerance;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Fallback allows the user to execute an alternative function in case the provided supplier fails.
 *
 * @param <T> type of the values returned
 */
public interface Fallback<T> extends FtHandlerTyped<T> {
    /**
     * A builder to customize {@link io.helidon.nima.faulttolerance.Fallback}.
     *
     * @param <T> type of the values returned by the failing method
     * @return a new builder
     */
    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Create a fallback for a callable.
     *
     * @param fallback fallback supplier to obtain the alternative result
     * @param <T>      type of the result
     * @return a new fallback
     */
    static <T> Fallback<T> create(Function<Throwable, ? extends T> fallback) {
        Builder<T> builder = builder();
        return builder.fallback(fallback).build();
    }

    /**
     * Fluent API builder for {@link io.helidon.nima.faulttolerance.Fallback}.
     *
     * @param <T> type of the values returned
     */
    class Builder<T> implements io.helidon.common.Builder<Builder<T>, Fallback<T>> {
        private final Set<Class<? extends Throwable>> applyOn = new HashSet<>();
        private final Set<Class<? extends Throwable>> skipOn = new HashSet<>();

        private Function<Throwable, ? extends T> fallback;

        private Builder() {
        }

        @Override
        public Fallback<T> build() {
            return new FallbackImpl<>(this);
        }

        /**
         * Configure a fallback for a type.
         *
         * @param fallback fallback supplier to obtain the alternative result
         * @return updated builder instance
         */
        public Builder<T> fallback(Function<Throwable, ? extends T> fallback) {
            this.fallback = fallback;
            return this;
        }

        /**
         * Apply fallback on these throwable classes.
         * Cannot be combined with {@link #skipOn(Class[])}.
         *
         * @param classes classes to fallback on
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder<T> applyOn(Class<? extends Throwable>... classes) {
            applyOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addApplyOn);

            return this;
        }

        /**
         * Apply fallback on this throwable class.
         *
         * @param clazz class to fallback on
         * @return updated builder instance
         */
        public Builder<T> addApplyOn(Class<? extends Throwable> clazz) {
            this.applyOn.add(clazz);
            return this;
        }

        /**
         * Do not apply fallback on these throwable classes.
         * Cannot be combined with {@link #applyOn(Class[])}.
         *
         * @param classes classes not to fallback on
         * @return updated builder instance
         */
        @SafeVarargs
        public final Builder<T> skipOn(Class<? extends Throwable>... classes) {
            skipOn.clear();
            Arrays.stream(classes)
                    .forEach(this::addSkipOn);

            return this;
        }

        /**
         * Do not apply fallback on this throwable class.
         *
         * @param clazz class not to fallback on
         * @return updated builder instance
         */
        public Builder<T> addSkipOn(Class<? extends Throwable> clazz) {
            this.skipOn.add(clazz);
            return this;
        }

        Set<Class<? extends Throwable>> applyOn() {
            return applyOn;
        }

        Set<Class<? extends Throwable>> skipOn() {
            return skipOn;
        }

        Function<Throwable, ? extends T> fallback() {
            return fallback;
        }
    }
}
