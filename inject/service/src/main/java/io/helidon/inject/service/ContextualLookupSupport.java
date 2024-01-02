/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.service;

import java.util.Objects;

import io.helidon.builder.api.Prototype;

final class ContextualLookupSupport {
    private ContextualLookupSupport() {
    }

    static final class CustomMethods {
        /**
         * Denotes a match to any (default) service.
         */
        @Prototype.Constant
        static final ContextualLookup EMPTY = createEmpty();

        private CustomMethods() {
        }

        /**
         * Creates a contextual service query given the injection point.
         *
         * @param injectionPoint the injection point info
         * @return the query
         */
        @Prototype.FactoryMethod
        static ContextualLookup create(Ip injectionPoint) {
            Objects.requireNonNull(injectionPoint);
            return ContextualLookup.builder()
                    .from(Lookup.create(injectionPoint))
                    .injectionPoint(injectionPoint)
                    .build();
        }

        private static ContextualLookup createEmpty() {
            return ContextualLookup.builder()
                    .from(Lookup.EMPTY)
                    .build();
        }
    }
}
