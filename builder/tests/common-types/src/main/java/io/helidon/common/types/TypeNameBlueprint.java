/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * TypeName is similar to {@link java.lang.reflect.Type} in its most basic use case. The {@link #name()} returns the package +
 * class name tuple for the given type (i.e., the canonical type name).
 * <p>
 * This class also provides a number of methods that are typically found in {@link Class} that can be used to avoid
 * classloading resolution:
 * <ul>
 * <li>{@link #packageName()} and {@link #className()} - access to the package and simple class names.</li>
 * <li>{@link #primitive()} and {@link #array()} - access to flags that is typically found in {@link Class}.</li>
 * </ul>
 * Additionally, this class offers a number of additional methods that are useful for handling generics:
 * <ul>
 * <li>{@link #generic()} - true when this type is declared to include generics (i.e., has type arguments).</li>
 * <li>{@link #wildcard()} - true if using wildcard generics usage.</li>
 * <li>{@link #typeArguments()} - access to generics / parametrized type information.</li>
 * </ul>
 * Finally, this class offers a number of methods that are helpful for code generation:
 * <ul>
 * <li>{@link #declaredName()} and {@link #resolvedName()}.</li>
 * </ul>
 */
@Prototype.Blueprint(decorator = TypeNameSupport.Decorator.class)
@Prototype.CustomMethods(TypeNameSupport.class)
@Prototype.Implement("java.lang.Comparable<TypeName>")
interface TypeNameBlueprint {
    /**
     * Functions the same as {@link Class#getPackageName()}.
     *
     * @return the package name, never null
     */
    @Option.Default("") // default value is empty package
    String packageName();

    /**
     * Functions the same as {@link Class#getSimpleName()}.
     *
     * @return the simple class name
     */
    @Option.Required
    String className();

    /**
     * Class name with enclosing types, separated by {@code .}.
     * If we have an inner class {@code Builder} of class {@code Type}, this method would return
     * {@code Type.Builder}.
     *
     * @return class name with enclosing types
     */
    default String classNameWithEnclosingNames() {
        List<String> allNames = new ArrayList<>(enclosingNames());
        allNames.add(className());
        return String.join(".", allNames);
    }

    /**
     * Simple names of enclosing classes (if any exist).
     * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
     * a list of {@code Type, NestOne}.
     *
     * @return enclosing classes simple names
     */
    @Option.Singular
    List<String> enclosingNames();

    /**
     * Functions the same as {@link Class#isPrimitive()}.
     *
     * @return true if this type represents a primitive type
     */
    @Option.DefaultBoolean(false)
    boolean primitive();

    /**
     * Functions the same as {@link Class#isArray()}.
     *
     * @return true if this type represents a primitive array []
     */
    @Option.DefaultBoolean(false)
    boolean array();

    /**
     * If this is a representation of {@link io.helidon.common.types.TypeName#array()}, this method can identify that it
     * was declared as a vararg.
     * This may be used for method/constructor parameters (which is the only place this is supported in Java).
     *
     * @return whether an array is declared as a vararg
     */
    @Option.DefaultBoolean(false)
    @Option.Redundant
    boolean vararg();

    /**
     * Indicates whether this type is using generics.
     *
     * @return used to represent a generic (e.g., "Optional&lt;CB&gt;")
     */
    @Option.Redundant
    @Option.DefaultBoolean(false)
    boolean generic();

    /**
     * Indicates whether this type is using wildcard generics.
     *
     * @return used to represent a wildcard (e.g., "? extends SomeType")
     */
    @Option.Redundant
    @Option.DefaultBoolean(false)
    boolean wildcard();

    /**
     * Returns the list of generic type arguments, or an empty list if no generics are in use.
     *
     * @return the type arguments of this type, if this type supports generics/parameterized type
     * @see #typeParameters()
     */
    @Option.Singular
    @Option.Redundant
    List<TypeName> typeArguments();

    /**
     * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
     * for example in declaration of the top level type (as arguments are a function of usage of the type).
     * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
     *
     * @return type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
     * @deprecated the {@link io.helidon.common.types.TypeName#typeArguments()} will contain all required information
     */
    @Option.Singular
    @Option.Redundant
    @Deprecated(forRemoval = true, since = "4.2.0")
    List<String> typeParameters();

    /**
     * Generic types that provide keyword {@code extends} will have a lower bound defined.
     * Each lower bound may be a real type, or another generic type.
     * <p>
     * This list may only have value if this is a generic type.
     *
     * @return list of lower bounds of this type
     * @see io.helidon.common.types.TypeName#generic()
     */
    @Option.Singular
    @Option.Redundant
    List<TypeName> lowerBounds();

    /**
     * Generic types that provide keyword {@code super} will have an upper bound defined.
     * Upper bound may be a real type, or another generic type.
     * <p>
     * This list may only have value if this is a generic type.
     *
     * @return list of upper bounds of this type
     * @see io.helidon.common.types.TypeName#generic()
     */
    @Option.Singular
    @Option.Redundant
    List<TypeName> upperBounds();

    /**
     * Indicates whether this type is a {@code java.util.List}.
     *
     * @return if this is a list
     */
    default boolean isList() {
        return TypeNames.LIST.name().equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Set}.
     *
     * @return if this is a set
     */
    default boolean isSet() {
        return TypeNames.SET.name().equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Map}.
     *
     * @return if this is a map
     */
    default boolean isMap() {
        return TypeNames.MAP.name().equals(name());
    }

    /**
     * Indicates whether this type is a {@code java.util.Optional}.
     *
     * @return if this is an optional
     */
    default boolean isOptional() {
        return TypeNames.OPTIONAL.name().equals(name());
    }

    /**
     * Indicates whether this type is a {@link java.util.function.Supplier}.
     *
     * @return if this is a supplier
     */
    default boolean isSupplier() {
        return TypeNames.SUPPLIER.fqName().equals(fqName());
    }

    /**
     * Simple class name with generic declaration (if part of this name).
     *
     * @return class name with generics, such as {@code Consumer<java.lang.String>}, or {@code Consumer<T>}
     */
    default String classNameWithTypes() {
        return className() + typeArgumentsDeclaration();
    }

    /**
     * The base name that includes the package name concatenated with the class name. Similar to
     * {@link java.lang.reflect.Type#getTypeName()}. Name contains possible enclosing types, separated
     * by {@code $}.
     *
     * @return the base type name given the set package and class name, but not including the generics/parameterized types
     */
    default String name() {
        // will be overridden in implementation, this is just ot prevent it from being part of builder
        return className();
    }

    /**
     * Typically used as part of code-gen, when ".class" is tacked onto the suffix of what this returns.
     *
     * @return same as getName() unless the type is an array, and then will add "[]" to the return
     */
    default String declaredName() {
        return array() ? (name() + "[]") : name();
    }

    /**
     * The fully qualified type name.
     *
     * @return the fully qualified name
     */
    default String fqName() {
        // implemented by a custom method
        return className();
    }

    /**
     * The fully resolved type. This will include the generic portion of the declaration, as well as any array
     * declaration, etc.
     *
     * @return the fully qualified name which includes the use of generics/parameterized types, arrays, etc.
     */
    default String resolvedName() {
        // implemented by a custom method
        return className();
    }

    private String typeArgumentsDeclaration() {
        if (typeArguments().isEmpty()) {
            return "";
        }
        String types = typeArguments()
                .stream()
                .map(TypeName::classNameWithTypes)
                .collect(Collectors.joining(", "));
        return "<" + types + ">";
    }
}
