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

package io.helidon.inject;

import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.inject.service.Ip;

final class ContextualServiceQuerySupport {
    private ContextualServiceQuerySupport() {
    }

    static final class CustomMethods {
        /**
         * Denotes a match to any (default) service, but required to be matched to at least one.
         */
        @Prototype.Constant
        static final ContextualServiceQuery REQUIRED = createRequired();
        /**
         * Denotes a match to any (default) service.
         */
        @Prototype.Constant
        static final ContextualServiceQuery EMPTY = createEmpty();
        private CustomMethods() {
        }

        /**
         * Creates a contextual service query given the injection point info.
         *
         * @param injectionPoint the injection point info
         * @param expected       true if the query is expected to at least have a single match
         * @return the query
         */
        @Prototype.FactoryMethod
        static ContextualServiceQuery create(Ip injectionPoint,
                                             boolean expected) {
            Objects.requireNonNull(injectionPoint);
            return ContextualServiceQuery.builder()
                    .from(Lookup.create(injectionPoint))
                    .expected(expected)
                    .injectionPoint(injectionPoint)
                    .build();
        }

        private static ContextualServiceQuery createRequired() {
            return ContextualServiceQuery.builder()
                    .from(Lookup.EMPTY)
                    .expected(true)
                    .build();
        }

        private static ContextualServiceQuery createEmpty() {
            return ContextualServiceQuery.builder()
                    .from(Lookup.EMPTY)
                    .build();
        }
    }
}
