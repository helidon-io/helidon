/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.api;

import java.util.Arrays;
import java.util.Optional;

/**
 * Types used from generated code.
 */
public final class GeneratedBuilder {
    private GeneratedBuilder() {
    }

    /**
     * Utility methods for equals and hash code of specific cases of field types.
     */
    public static final class EqualityUtil {
        private EqualityUtil() {
        }

        /**
         * Equals that uses {@link java.util.Arrays#equals(char[], char[])} in case both optionals have a value.
         *
         * @param first  first optional
         * @param second second optional
         * @return whether the optionals are equals
         */
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public static boolean optionalCharArrayEquals(Optional<char[]> first, Optional<char[]> second) {
            if (first.isEmpty() && second.isEmpty()) {
                return true;
            }
            if (first.isEmpty() || second.isEmpty()) {
                return false;
            }
            return Arrays.equals(first.get(), second.get());
        }

        /**
         * Hash code that uses {@link java.util.Arrays#hashCode(char[])} in case the optional has a value.
         *
         * @param instance instance to get hash code for
         * @return hash code that honors existence of char array
         */
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public static int optionalCharArrayHash(Optional<char[]> instance) {
            return instance.map(Arrays::hashCode)
                    .orElseGet(instance::hashCode);
        }
    }
}
