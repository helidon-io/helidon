/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.restclientmetrics;

import java.util.Optional;

import io.helidon.common.LazyValue;

class Utils {

    private Utils() {
    }

    /**
     * Wraps an {@link java.util.Optional} around the {@code lazyValue.get()} result, returning an
     * {@code Optional.of(lazyValue.get())} if the lazy value successfully returns its value and an empty
     * {@code Optional} if the lazy value cannot be loaded.
     *
     * @param lazyValue the {@link io.helidon.common.LazyValue} to wrap
     * @return an {@code Optional} as described above
     */
    static <T> Optional<T> optOf(LazyValue<T> lazyValue) {
        try {
            return Optional.of(lazyValue.get());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
