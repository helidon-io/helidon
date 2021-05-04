/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.media.common;

import io.helidon.common.GenericType;

/**
 * Conversion operator that can be selected based on a requested type and a
 * message body context.
 *
 * @param <T> Type supported by the operator
 */
public interface MessageBodyOperator<T extends MessageBodyContext> {

    /**
     * Test if the operator can convert the given type.
     *
     * @param type the requested type
     * @param context the context providing the headers abstraction
     * @return {@link PredicateResult} result
     */
    PredicateResult accept(GenericType<?> type, T context);

    /**
     * Status whether requested class type is supported by the operator.
     */
    enum PredicateResult {

        /**
         * Requested type not supported.
         */
        NOT_SUPPORTED,

        /**
         * Requested type is compatible with this operator, but it is not exact match.
         */
        COMPATIBLE,

        /**
         * Requested type is supported by that specific operator.
         */
        SUPPORTED;

        /**
         * Whether handled class is supported.
         * Method {@link Class#isAssignableFrom(Class)} is invoked to verify if class under expected parameter is
         * supported by by the class under actual parameter.
         *
         * @param expected expected type
         * @param actual actual type
         * @return if supported or not
         */
        public static PredicateResult supports(Class<?> expected, GenericType<?> actual) {
            return expected.isAssignableFrom(actual.rawType()) ? SUPPORTED : NOT_SUPPORTED;
        }

    }
}
