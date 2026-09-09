/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

/**
 * Runtime type created by {@link SealedConfig}.
 *
 * @param <T> configured value type marker
 */
public final class SealedRuntime<T> implements RuntimeType.Api<SealedConfig<T>> {
    private final SealedConfig<T> prototype;

    private SealedRuntime(SealedConfig<T> prototype) {
        this.prototype = prototype;
    }

    /**
     * Create a builder for a sealed prototype.
     *
     * @param <T> configured value type marker
     * @return a new builder
     */
    public static <T> SealedConfig.Builder<T> builder() {
        return SealedConfig.builder();
    }

    /**
     * Create a runtime instance by updating its prototype builder.
     *
     * @param consumer builder consumer
     * @param <T> configured value type marker
     * @return a new runtime instance
     */
    public static <T> SealedRuntime<T> create(Consumer<SealedConfig.Builder<T>> consumer) {
        return SealedRuntime.<T>builder()
                .update(consumer)
                .build();
    }

    /**
     * Create a runtime instance from its prototype.
     *
     * @param prototype runtime prototype
     * @param <T> configured value type marker
     * @return a new runtime instance
     */
    public static <T> SealedRuntime<T> create(SealedConfig<T> prototype) {
        return new SealedRuntime<>(prototype);
    }

    @Override
    public SealedConfig<T> prototype() {
        return prototype;
    }
}
