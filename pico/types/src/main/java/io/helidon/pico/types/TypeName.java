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

package io.helidon.pico.types;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * TypeName is similar to {@link java.lang.reflect.Type} in its most basic use case. The {@link #getName()} returns the package +
 * class name tuple for the given type (i.e., the canonical type name).
 * <p>
 *
 * This class also provides a number of methods that are typically found in {@link java.lang.Class} that can be used to avoid
 * classloading resolution:
 * <li>{@link #getPackageName()} and {@link #getClassName()} - access to the package and simple class names.
 * <li>{@link #isPrimitive()} and {@link #isArray()} - access to flags that is typically found in {@link java.lang.Class}.
 * <p>
 *
 * Additionally, this class offers a number of additional methods that are useful for handling generics:
 * <li>{@link #isGeneric()} - true when this type is declared to include generics (i.e., has type arguments).
 * <li>{@link #isWildcard()} - true if using wildcard generics usage.
 * <li>{@link #getTypeArguments()} - access to generics / parametrized type information.
 * <p>
 *
 * Finally, this class offers a number of methods that are helpful for code generation:
 * <li>{@link #getDeclaredName()} and {@link #getFQName()}.
 */
public interface TypeName extends Comparable<TypeName> {

    /**
     * Functions the same as {@link Class#getPackageName()}.
     *
     * @return the package name
     */
    @JsonIgnore
    String getPackageName();

    /**
     * Functions the same as {@link Class#getSimpleName()}.
     *
     * @return the simple class name
     */
    @JsonIgnore
    String getClassName();

    /**
     * Functions the same as {@link Class#isPrimitive()}.
     *
     * @return true if this type represents a primitive type.
     */
    @JsonIgnore
    boolean isPrimitive();

    /**
     * Functions the same as {@link Class#isArray()}.
     *
     * @return true if this type represents a primitive array [].
     */
    @JsonIgnore
    boolean isArray();

    /**
     * @return used to represent a generic (e.g., "Optional<CB>").
     */
    @JsonIgnore
    boolean isGeneric();

    /**
     * @return used to represent a wildcard (e.g., "? extends SomeType").
     */
    @JsonIgnore
    boolean isWildcard();

    /**
     * @return the type arguments of this type, if this type supports generics/parameterized type.
     */
    @JsonIgnore
    List<TypeName> getTypeArguments();

    /**
     * Typically used as part of code-gen, when ".class" is tacked onto the suffix of what this returns.
     *
     * @return same as getName() unless the type is an array, and then will add "[]" to the return.
     */
    @JsonIgnore
    String getDeclaredName();

    /**
     * @return the fully qualified name which includes the use of generics/parameterized types, arrays, etc.
     */
    @JsonIgnore
    String getFQName();

    /**
     * @return the base type name given the set package and class name, but not including the generics/parameterized types.
     */
    String getName();

}
