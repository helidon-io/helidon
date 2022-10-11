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

/**
 * TypeName is similar to {@link java.lang.reflect.Type} in its most basic use case. The {@link #getName()} returns the package +
 * class name tuple for the given type (i.e., the canonical type name).
 * <p>
 *
 * This class also provides a number of methods that are typically found in {@link java.lang.Class} that can be used to avoid
 * classloading resolution:
 * <li>{@link #packageName()} and {@link #className()} - access to the package and simple class names.
 * <li>{@link #primitive()} and {@link #array()} - access to flags that is typically found in {@link java.lang.Class}.
 * <p>
 *
 * Additionally, this class offers a number of additional methods that are useful for handling generics:
 * <li>{@link #generic()} - true when this type is declared to include generics (i.e., has type arguments).
 * <li>{@link #wildcard()} - true if using wildcard generics usage.
 * <li>{@link #typeArguments()} - access to generics / parametrized type information.
 * <p>
 *
 * Finally, this class offers a number of methods that are helpful for code generation:
 * <li>{@link #declaredName()} and {@link #fqName()}.
 */
public interface TypeName extends Comparable<TypeName> {

    /**
     * Functions the same as {@link Class#getPackageName()}.
     *
     * @return the package name
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
     * @return used to represent a generic (e.g., "Optional<CB>").
     */
    boolean generic();

    /**
     * @return used to represent a wildcard (e.g., "? extends SomeType").
     */
    boolean wildcard();

    /**
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
     * @return the fully qualified name which includes the use of generics/parameterized types, arrays, etc.
     */
    String fqName();

    /**
     * @return the base type name given the set package and class name, but not including the generics/parameterized types.
     */
    String getName();

}
