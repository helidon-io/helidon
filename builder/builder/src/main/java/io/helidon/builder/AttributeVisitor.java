/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.builder;

import java.util.Map;
import java.util.function.Supplier;

/**
 * A functional interface that can be used to visit all attributes of this type.
 * <p>
 * This type is used when {@link Builder#requireLibraryDependencies()} is used. When it is turned off, however, an equivalent
 * type will be code-generated into each generated bean.
 *
 * @param <T> type of the user defined context this attribute visitor supports
 */
@FunctionalInterface
// important note: this class is also code generated - please keep this in synch with generated code
public interface AttributeVisitor<T> {

    /**
     * Visits the attribute named 'attrName'.
     *
     * @param attrName          the attribute name
     * @param valueSupplier     the attribute value supplier
     * @param meta              the meta information for the attribute
     * @param userDefinedCtx    a user defined context that can be used for holding an object of your choosing
     * @param type              the type of the attribute
     * @param typeArgument      the type arguments (if type is a parameterized / generic type)
     */
    void visit(String attrName,
               Supplier<Object> valueSupplier,
               Map<String, Object> meta,
               T userDefinedCtx,
               Class<?> type,
               Class<?>... typeArgument);

}
