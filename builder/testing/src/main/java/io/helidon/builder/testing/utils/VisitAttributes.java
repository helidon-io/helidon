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

package io.helidon.builder.testing.utils;

import io.helidon.builder.AttributeVisitor;

/**
 * The functional interface that all {@link io.helidon.builder.Builder}-generated targets are expected to exhibit
 * (but not implement).
 *
 * @param <T> the user defined type
 */
@FunctionalInterface
public interface VisitAttributes<T> {

    /**
     * Visits the methods on the builder-generated target type.
     *
     * @param visitor        the visitor
     * @param userDefinedCtx the user defined context
     */
    void visitAttributes(AttributeVisitor<T> visitor, T userDefinedCtx);

}
