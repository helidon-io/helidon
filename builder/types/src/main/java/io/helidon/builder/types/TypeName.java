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

package io.helidon.builder.types;

import java.util.List;

/**
 * TypeName is similar to {@link java.lang.reflect.Type} in its most basic use case. The {@link #name()} returns the package +
 * class name tuple for the given type (i.e., the canonical type name).
 * <p>
 * This class also provides a number of methods that are typically found in {@link java.lang.Class} that can be used to avoid
 * classloading resolution:
 * <ul>
 * <li>{@link #packageName()} and {@link #className()} - access to the package and simple class names.</li>
 * <li>{@link #primitive()} and {@link #array()} - access to flags that is typically found in {@link java.lang.Class}.</li>
 * </ul>
 * Additionally, this class offers a number of additional methods that are useful for handling generics:
 * <ul>
 * <li>{@link #generic()} - true when this type is declared to include generics (i.e., has type arguments).</li>
 * <li>{@link #wildcard()} - true if using wildcard generics usage.</li>
 * <li>{@link #typeArguments()} - access to generics / parametrized type information.</li>
 * </ul>
 * Finally, this class offers a number of methods that are helpful for code generation:
 * <ul>
 * <li>{@link #declaredName()} and {@link #fqName()}.</li>
 * </ul>
 */
public interface TypeName extends Comparable<TypeName> {

    /**
     * Functions the same as {@link Class#getPackageName()}.
     *
     * @return the package name, never null
     */
    String packageName();

    /**
     * Functions the same as {@link Class#getSimpleName()}.
     *
     * @return the simple class name
     */
    String className();

    /**
     * Functions the same as {@link Class#isPrimitive()}.
     *
     * @return true if this type represents a primitive type.
     */
    boolean primitive();

    /**
     * Functions the same as {@link Class#isArray()}.
     *
     * @return true if this type represents a primitive array [].
     */
    boolean array();

    /**
     * Indicates whether this type is using generics.
     *
     * @return used to represent a generic (e.g., "Optional&lt;CB&gt;").
     */
    boolean generic();

    /**
     * Indicates whether this type is using wildcard generics.
     *
     * @return used to represent a wildcard (e.g., "? extends SomeType").
     */
    boolean wildcard();

    /**
     * Indicates whether this type is a {@code java.util.List}.
     *
     * @return if this is a list
     */
    default boolean isList() {
        return "java.util.List".equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Set}.
     *
     * @return if this is a set
     */
    default boolean isSet() {
        return "java.util.Set".equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Map}.
     *
     * @return if this is a map
     */
    default boolean isMap() {
        return "java.util.Map".equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Optional}.
     *
     * @return if this is an optional
     */
    default boolean isOptional() {
        return "java.util.Optional".equals(name());
    }

    /**
     * Returns the list of generic type parameters, or an empty list if no generics are in use.
     *
     * @return the type arguments of this type, if this type supports generics/parameterized type.
     */
    List<TypeName> typeArguments();

    /**
     * Typically used as part of code-gen, when ".class" is tacked onto the suffix of what this returns.
     *
     * @return same as getName() unless the type is an array, and then will add "[]" to the return.
     */
    String declaredName();

    /**
     * The fully qualified type name. This will include the generic portion of the declaration, as well as any array declaration, etc.
     *
     * @return the fully qualified name which includes the use of generics/parameterized types, arrays, etc.
     */
    String fqName();

    /**
     * The base name that includes the package name concatenated with the class name. Similar to
     * {@link java.lang.reflect.Type#getTypeName()}.
     *
     * @return the base type name given the set package and class name, but not including the generics/parameterized types.
     */
    String name();

}
