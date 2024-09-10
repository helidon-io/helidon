/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.util.List;

/**
 * Signature of a {@link io.helidon.common.types.TypedElementInfo}.
 * <p>
 * The {@link io.helidon.common.types.TypedElementInfo#signature()} is intended to compare
 * fields, methods, and constructors across type hierarchy - for example when looking for a method
 * that we override.
 * <p>
 * The following information is used for equals and hash-code:
 * <ul>
 *     <li>Field: field name</li>
 *     <li>Constructor: parameter types</li>
 *     <li>Method: method name, parameter types</li>
 *     <li>Parameter: this signature is not useful, as we cannot depend on parameter names</li>
 * </ul>
 *
 * The signature has well-defined {@code hashCode} and {@code equals} methods,
 * so it can be safely used as a key in a {@link java.util.Map}.
 * <p>
 * This interface is sealed, an instance can only be obtained
 * from {@link io.helidon.common.types.TypedElementInfo#signature()}.
 *
 * @see #text()
 */
public sealed interface ElementSignature permits ElementSignatures.FieldSignature,
                                                 ElementSignatures.MethodSignature,
                                                 ElementSignatures.ParameterSignature,
                                                 ElementSignatures.NoSignature {
    /**
     * Type of the element. Resolves as follows:
     * <ul>
     *     <li>Field: type of the field</li>
     *     <li>Constructor: void</li>
     *     <li>Method: method return type</li>
     *     <li>Parameter: parameter type</li>
     * </ul>
     *
     * @return type of this element, never used for equals or hashCode
     */
    TypeName type();

    /**
     * Name of the element. For constructor, this always returns {@code <init>},
     * for parameters, this method may return the real parameter name or an index
     * parameter name depending on the source of the information (during annotation processing,
     * this would be the actual parameter name, when classpath scanning, this would be something like
     * {@code param0}.
     *
     * @return name of this element
     */
    String name();

    /**
     * Types of parameters if this represents a method or a constructor,
     * empty {@link java.util.List} otherwise.
     *
     * @return parameter types
     */
    List<TypeName> parameterTypes();

    /**
     * A text representation of this signature.
     *
     * <ul>
     *     <li>Field: field name (such as {@code myNiceField}</li>
     *     <li>Constructor: comma separated parameter types (no generics) in parentheses (such as
     *     {@code (java.lang.String,java.util.List)})</li>
     *     <li>Method: method name, parameter types (no generics) in parentheses (such as
     *     {@code methodName(java.lang.String,java.util.List)}</li>
     *     <li>Parameter: parameter name (such as {@code myParameter} or {@code param0} - not very useful, as parameter names
     *     are not carried over to compiled code in Java</li>
     * </ul>
     *
     * @return text representation
     */
    String text();
}
