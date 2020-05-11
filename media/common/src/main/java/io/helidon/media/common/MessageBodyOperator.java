/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
     * TODO javadoc
     */
    enum PredicateResult {
        NOT_SUPPORTED,
        ASSIGNABLE,
        EXACT
    }
}
