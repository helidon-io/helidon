/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;

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
 *
 * @see #builder()
 */
public interface TypeName extends TypeNameBlueprint, Prototype.Api, Comparable<TypeName> {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static TypeName.Builder builder() {
        return new TypeName.Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static TypeName.Builder builder(TypeName instance) {
        return TypeName.builder().from(instance);
    }

    /**
     * Create a type name from a type (such as class).
     *
     * @param type the type
     * @return type name for the provided type
     */
    static TypeName create(Type type) {
        return TypeNameSupport.create(type);
    }

    /**
     * Creates a type name from a fully qualified class name.
     *
     * @param typeName the FQN of the class type
     * @return the TypeName for the provided type name
     */
    static TypeName create(String typeName) {
        return TypeNameSupport.create(typeName);
    }

    /**
     * Creates a type name from a generic alias type name.
     *
     * @param genericAliasTypeName the generic alias type name
     * @return the TypeName for the provided type name
     */
    static TypeName createFromGenericDeclaration(String genericAliasTypeName) {
        return TypeNameSupport.createFromGenericDeclaration(genericAliasTypeName);
    }

    /**
     * Compare with another type name.
     * First compares by {@link io.helidon.common.types.TypeName#name()}, than by
     * {@link io.helidon.common.types.TypeName#primitive}, and finally by {@link io.helidon.common.types.TypeName#array()}.
     *
     * @param o type name to compare to
     * @return comparison result
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    default int compareTo(TypeName o) {
        return TypeNameSupport.compareTo(this, o);
    }

    /**
     * Return the boxed equivalent of this type.
     * If this is not a primitive type, returns this instance.
     *
     * @return boxed type for this type, or this type if not primitive
     */
    default TypeName boxed() {
        return TypeNameSupport.boxed(this);
    }

    /**
     * Return the unboxed equivalent of this type.
     * If this is a boxed primitive, the primitive type is returned.
     *
     * @return primitive type for this type, or this type if not boxed primitive type
     */
    default TypeName unboxed() {
        return TypeNameSupport.unboxed(this);
    }

    /**
     * The base name that includes the package name concatenated with the class name. Similar to
     * {@link java.lang.reflect.Type#getTypeName()}. Name contains possible enclosing types, separated
     * by {@code $}.
     *
     * @return the base type name given the set package and class name, but not including the generics/parameterized types
     */
    @Override
    default String name() {
        return TypeNameSupport.name(this);
    }

    /**
     * The base generic type name, stripped of any {@link TypeName#typeArguments()}.
     * This is equivalent to the type name represented by {@link TypeName#name()}.
     *
     * @return based generic type name
     */
    default TypeName genericTypeName() {
        return TypeNameSupport.genericTypeName(this);
    }

    /**
     * The fully qualified type name.
     *
     * @return the fully qualified name
     */
    @Override
    default String fqName() {
        return TypeNameSupport.fqName(this);
    }

    /**
     * Typically used as part of code-gen, when ".class" is tacked onto the suffix of what this returns.
     *
     * @return same as getName() unless the type is an array, and then will add "[]" to the return
     */
    @Override
    default String declaredName() {
        return TypeNameSupport.declaredName(this);
    }

    /**
     * The fully resolved type. This will include the generic portion of the declaration, as well as any array
     * declaration, etc.
     *
     * @return the fully qualified name which includes the use of generics/parameterized types, arrays, etc.
     */
    @Override
    default String resolvedName() {
        return TypeNameSupport.resolvedName(this);
    }

    /**
     * Class name with enclosing types, separated by {@code .}.
     * If we have an inner class {@code Builder} of class {@code Type}, this method would return
     * {@code Type.Builder}.
     *
     * @return class name with enclosing types
     */
    default String classNameWithEnclosingNames() {
        return TypeNameBlueprint.super.classNameWithEnclosingNames();
    }

    /**
     * Indicates whether this type is a {@code java.util.List}.
     *
     * @return if this is a list
     */
    default boolean isList() {
        return TypeNameBlueprint.super.isList();
    }

    /**
     * Indicates whether this type is a {@code java.util.Set}.
     *
     * @return if this is a set
     */
    default boolean isSet() {
        return TypeNameBlueprint.super.isSet();
    }

    /**
     * Indicates whether this type is a {@code java.util.Map}.
     *
     * @return if this is a map
     */
    default boolean isMap() {
        return TypeNameBlueprint.super.isMap();
    }

    /**
     * Indicates whether this type is a {@code java.util.Optional}.
     *
     * @return if this is an optional
     */
    default boolean isOptional() {
        return TypeNameBlueprint.super.isOptional();
    }

    /**
     * Indicates whether this type is a {@link java.util.function.Supplier}.
     *
     * @return if this is a supplier
     */
    default boolean isSupplier() {
        return TypeNameBlueprint.super.isSupplier();
    }

    /**
     * Simple class name with generic declaration (if part of this name).
     *
     * @return class name with generics, such as {@code Consumer<java.lang.String>}, or {@code Consumer<T>}
     */
    default String classNameWithTypes() {
        return TypeNameBlueprint.super.classNameWithTypes();
    }

    /**
     * Functions similar to {@link Class#getPackageName()}.
     *
     * @return the package name, never null
     */
    @Override
    String packageName();

    /**
     * Functions similar to {@link Class#getSimpleName()}.
     *
     * @return the simple class name
     */
    @Override
    String className();

    /**
     * Simple names of enclosing classes (if any exist).
     * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
     * a list of {@code Type, NestOne}.
     *
     * @return enclosing classes simple names
     */
    @Override
    List<String> enclosingNames();

    /**
     * Functions similar to {@link Class#isPrimitive()}.
     *
     * @return true if this type represents a primitive type
     */
    @Override
    boolean primitive();

    /**
     * Functions similar to {@link Class#isArray()}.
     *
     * @return true if this type represents a primitive array []
     */
    @Override
    boolean array();

    /**
     * If this is a representation of {@link io.helidon.common.types.TypeName#array()}, this method can identify that it
     * was declared as a vararg.
     * This may be used for method/constructor parameters (which is the only place this is supported in Java).
     *
     * @return whether an array is declared as a vararg
     */
    @Override
    boolean vararg();

    /**
     * Indicates whether this type is using generics.
     *
     * @return used to represent a generic (e.g., "Optional&lt;CB&gt;")
     */
    @Override
    boolean generic();

    /**
     * Indicates whether this type is using wildcard generics.
     *
     * @return used to represent a wildcard (e.g., "? extends SomeType")
     */
    @Override
    boolean wildcard();

    /**
     * Returns the list of generic type arguments, or an empty list if no generics are in use.
     *
     * @return the type arguments of this type, if this type supports generics/parameterized type
     * @see #typeParameters()
     */
    @Override
    List<TypeName> typeArguments();

    /**
     * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
     * for example in declaration of the top level type (as arguments are a function of usage of the type).
     * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
     *
     * @return type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
     */
    @Override
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
    @Override
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
    @Override
    List<TypeName> upperBounds();

    /**
     * Component type of array.
     *
     * @return component type of array
     */
    @Override
    Optional<TypeName> componentType();

    /**
     * Fluent API builder base for {@link TypeName}.
     *
     * @param <BUILDER>   type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends TypeName.BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeName>
            implements Prototype.Builder<BUILDER, PROTOTYPE> {

        private final List<Annotation> annotations = new ArrayList<>();
        private final List<Annotation> inheritedAnnotations = new ArrayList<>();
        private final List<TypeName> lowerBounds = new ArrayList<>();
        private final List<TypeName> typeArguments = new ArrayList<>();
        private final List<TypeName> upperBounds = new ArrayList<>();
        private final List<String> enclosingNames = new ArrayList<>();
        private final List<String> typeParameters = new ArrayList<>();
        private boolean array = false;
        private boolean generic = false;
        private boolean isAnnotationsMutated;
        private boolean isEnclosingNamesMutated;
        private boolean isInheritedAnnotationsMutated;
        private boolean isLowerBoundsMutated;
        private boolean isTypeArgumentsMutated;
        private boolean isTypeParametersMutated;
        private boolean isUpperBoundsMutated;
        private boolean primitive = false;
        private boolean vararg = false;
        private boolean wildcard = false;
        private String className;
        private String packageName = "";
        private TypeName componentType;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance. This method disables automatic service discovery.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypeName prototype) {
            packageName(prototype.packageName());
            className(prototype.className());
            if (!this.isEnclosingNamesMutated) {
                this.enclosingNames.clear();
            }
            addEnclosingNames(prototype.enclosingNames());
            primitive(prototype.primitive());
            array(prototype.array());
            vararg(prototype.vararg());
            generic(prototype.generic());
            wildcard(prototype.wildcard());
            if (!this.isTypeArgumentsMutated) {
                this.typeArguments.clear();
            }
            addTypeArguments(prototype.typeArguments());
            if (!this.isTypeParametersMutated) {
                this.typeParameters.clear();
            }
            addTypeParameters(prototype.typeParameters());
            if (!this.isLowerBoundsMutated) {
                this.lowerBounds.clear();
            }
            addLowerBounds(prototype.lowerBounds());
            if (!this.isUpperBoundsMutated) {
                this.upperBounds.clear();
            }
            addUpperBounds(prototype.upperBounds());
            componentType(prototype.componentType());
            if (!this.isAnnotationsMutated) {
                this.annotations.clear();
            }
            addAnnotations(prototype.annotations());
            if (!this.isInheritedAnnotationsMutated) {
                this.inheritedAnnotations.clear();
            }
            addInheritedAnnotations(prototype.inheritedAnnotations());
            return self();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypeName.BuilderBase<?, ?> builder) {
            packageName(builder.packageName());
            builder.className().ifPresent(this::className);
            if (this.isEnclosingNamesMutated) {
                if (builder.isEnclosingNamesMutated) {
                    addEnclosingNames(builder.enclosingNames());
                }
            } else {
                enclosingNames(builder.enclosingNames());
            }
            primitive(builder.primitive());
            array(builder.array());
            vararg(builder.vararg());
            generic(builder.generic());
            wildcard(builder.wildcard());
            if (this.isTypeArgumentsMutated) {
                if (builder.isTypeArgumentsMutated) {
                    addTypeArguments(builder.typeArguments());
                }
            } else {
                typeArguments(builder.typeArguments());
            }
            if (this.isTypeParametersMutated) {
                if (builder.isTypeParametersMutated) {
                    addTypeParameters(builder.typeParameters());
                }
            } else {
                typeParameters(builder.typeParameters());
            }
            if (this.isLowerBoundsMutated) {
                if (builder.isLowerBoundsMutated) {
                    addLowerBounds(builder.lowerBounds());
                }
            } else {
                lowerBounds(builder.lowerBounds());
            }
            if (this.isUpperBoundsMutated) {
                if (builder.isUpperBoundsMutated) {
                    addUpperBounds(builder.upperBounds());
                }
            } else {
                upperBounds(builder.upperBounds());
            }
            builder.componentType().ifPresent(this::componentType);
            if (this.isAnnotationsMutated) {
                if (builder.isAnnotationsMutated) {
                    addAnnotations(builder.annotations());
                }
            } else {
                annotations(builder.annotations());
            }
            if (this.isInheritedAnnotationsMutated) {
                if (builder.isInheritedAnnotationsMutated) {
                    addInheritedAnnotations(builder.inheritedAnnotations());
                }
            } else {
                inheritedAnnotations(builder.inheritedAnnotations());
            }
            return self();
        }

        /**
         * Update builder from the provided type.
         *
         * @param type    type to get information (package name, class name, primitive, array)
         * @return updated builder instance
         */
        public BUILDER type(Type type) {
            TypeNameSupport.type(this, type);
            return self();
        }

        /**
         * Functions similar to {@link Class#getPackageName()}.
         *
         * @param packageName the package name, never null
         * @return updated builder instance
         * @see #packageName()
         */
        public BUILDER packageName(String packageName) {
            Objects.requireNonNull(packageName);
            this.packageName = packageName;
            return self();
        }

        /**
         * Functions similar to {@link Class#getSimpleName()}.
         *
         * @param className the simple class name
         * @return updated builder instance
         * @see #className()
         */
        public BUILDER className(String className) {
            Objects.requireNonNull(className);
            this.className = className;
            return self();
        }

        /**
         * Clear all enclosingNames.
         *
         * @return updated builder instance
         * @see #enclosingNames()
         */
        public BUILDER clearEnclosingNames() {
            this.isEnclosingNamesMutated = true;
            this.enclosingNames.clear();
            return self();
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @param enclosingNames enclosing classes simple names
         * @return updated builder instance
         * @see #enclosingNames()
         */
        public BUILDER enclosingNames(List<String> enclosingNames) {
            Objects.requireNonNull(enclosingNames);
            this.isEnclosingNamesMutated = true;
            this.enclosingNames.clear();
            this.enclosingNames.addAll(enclosingNames);
            return self();
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @param enclosingNames enclosing classes simple names
         * @return updated builder instance
         * @see #enclosingNames()
         */
        public BUILDER addEnclosingNames(List<String> enclosingNames) {
            Objects.requireNonNull(enclosingNames);
            this.isEnclosingNamesMutated = true;
            this.enclosingNames.addAll(enclosingNames);
            return self();
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @param enclosingName add single enclosing classes simple names
         * @return updated builder instance
         * @see #enclosingNames()
         */
        public BUILDER addEnclosingName(String enclosingName) {
            Objects.requireNonNull(enclosingName);
            this.enclosingNames.add(enclosingName);
            this.isEnclosingNamesMutated = true;
            return self();
        }

        /**
         * Functions similar to {@link Class#isPrimitive()}.
         *
         * @param primitive true if this type represents a primitive type
         * @return updated builder instance
         * @see #primitive()
         */
        public BUILDER primitive(boolean primitive) {
            this.primitive = primitive;
            return self();
        }

        /**
         * Functions similar to {@link Class#isArray()}.
         *
         * @param array true if this type represents a primitive array []
         * @return updated builder instance
         * @see #array()
         */
        public BUILDER array(boolean array) {
            this.array = array;
            return self();
        }

        /**
         * If this is a representation of {@link io.helidon.common.types.TypeName#array()}, this method can identify that it
         * was declared as a vararg.
         * This may be used for method/constructor parameters (which is the only place this is supported in Java).
         *
         * @param vararg whether an array is declared as a vararg
         * @return updated builder instance
         * @see #vararg()
         */
        public BUILDER vararg(boolean vararg) {
            this.vararg = vararg;
            return self();
        }

        /**
         * Indicates whether this type is using generics.
         *
         * @param generic used to represent a generic (e.g., "Optional&lt;CB&gt;")
         * @return updated builder instance
         * @see #generic()
         */
        public BUILDER generic(boolean generic) {
            this.generic = generic;
            return self();
        }

        /**
         * Indicates whether this type is using wildcard generics.
         *
         * @param wildcard used to represent a wildcard (e.g., "? extends SomeType")
         * @return updated builder instance
         * @see #wildcard()
         */
        public BUILDER wildcard(boolean wildcard) {
            this.wildcard = wildcard;
            return self();
        }

        /**
         * Clear all typeArguments.
         *
         * @return updated builder instance
         * @see #typeParameters()
         * @see #typeArguments()
         */
        public BUILDER clearTypeArguments() {
            this.isTypeArgumentsMutated = true;
            this.typeArguments.clear();
            return self();
        }

        /**
         * Returns the list of generic type arguments, or an empty list if no generics are in use.
         *
         * @param typeArguments the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeParameters()
         * @see #typeArguments()
         */
        public BUILDER typeArguments(List<? extends TypeName> typeArguments) {
            Objects.requireNonNull(typeArguments);
            this.isTypeArgumentsMutated = true;
            this.typeArguments.clear();
            this.typeArguments.addAll(typeArguments);
            return self();
        }

        /**
         * Returns the list of generic type arguments, or an empty list if no generics are in use.
         *
         * @param typeArguments the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeParameters()
         * @see #typeArguments()
         */
        public BUILDER addTypeArguments(List<? extends TypeName> typeArguments) {
            Objects.requireNonNull(typeArguments);
            this.isTypeArgumentsMutated = true;
            this.typeArguments.addAll(typeArguments);
            return self();
        }

        /**
         * Returns the list of generic type arguments, or an empty list if no generics are in use.
         *
         * @param typeArgument add single the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeParameters()
         * @see #typeArguments()
         */
        public BUILDER addTypeArgument(TypeName typeArgument) {
            Objects.requireNonNull(typeArgument);
            this.typeArguments.add(typeArgument);
            this.isTypeArgumentsMutated = true;
            return self();
        }

        /**
         * Returns the list of generic type arguments, or an empty list if no generics are in use.
         *
         * @param consumer consumer of builder for the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeParameters()
         * @see #typeArguments()
         */
        public BUILDER addTypeArgument(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.addTypeArgument(builder.build());
            return self();
        }

        /**
         * Clear all typeParameters.
         *
         * @return updated builder instance
         * @see #typeParameters()
         */
        public BUILDER clearTypeParameters() {
            this.isTypeParametersMutated = true;
            this.typeParameters.clear();
            return self();
        }

        /**
         * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
         * for example in declaration of the top level type (as arguments are a function of usage of the type).
         * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
         *
         * @param typeParameters type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
         * @return updated builder instance
         * @see #typeParameters()
         */
        public BUILDER typeParameters(List<String> typeParameters) {
            Objects.requireNonNull(typeParameters);
            this.isTypeParametersMutated = true;
            this.typeParameters.clear();
            this.typeParameters.addAll(typeParameters);
            return self();
        }

        /**
         * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
         * for example in declaration of the top level type (as arguments are a function of usage of the type).
         * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
         *
         * @param typeParameters type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
         * @return updated builder instance
         * @see #typeParameters()
         */
        public BUILDER addTypeParameters(List<String> typeParameters) {
            Objects.requireNonNull(typeParameters);
            this.isTypeParametersMutated = true;
            this.typeParameters.addAll(typeParameters);
            return self();
        }

        /**
         * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
         * for example in declaration of the top level type (as arguments are a function of usage of the type).
         * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
         *
         * @param typeParameter add single type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
         * @return updated builder instance
         * @see #typeParameters()
         */
        public BUILDER addTypeParameter(String typeParameter) {
            Objects.requireNonNull(typeParameter);
            this.typeParameters.add(typeParameter);
            this.isTypeParametersMutated = true;
            return self();
        }

        /**
         * Clear all lowerBounds.
         *
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #lowerBounds()
         */
        public BUILDER clearLowerBounds() {
            this.isLowerBoundsMutated = true;
            this.lowerBounds.clear();
            return self();
        }

        /**
         * Generic types that provide keyword {@code extends} will have a lower bound defined.
         * Each lower bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param lowerBounds list of lower bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #lowerBounds()
         */
        public BUILDER lowerBounds(List<? extends TypeName> lowerBounds) {
            Objects.requireNonNull(lowerBounds);
            this.isLowerBoundsMutated = true;
            this.lowerBounds.clear();
            this.lowerBounds.addAll(lowerBounds);
            return self();
        }

        /**
         * Generic types that provide keyword {@code extends} will have a lower bound defined.
         * Each lower bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param lowerBounds list of lower bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #lowerBounds()
         */
        public BUILDER addLowerBounds(List<? extends TypeName> lowerBounds) {
            Objects.requireNonNull(lowerBounds);
            this.isLowerBoundsMutated = true;
            this.lowerBounds.addAll(lowerBounds);
            return self();
        }

        /**
         * Generic types that provide keyword {@code extends} will have a lower bound defined.
         * Each lower bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param lowerBound add single list of lower bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #lowerBounds()
         */
        public BUILDER addLowerBound(TypeName lowerBound) {
            Objects.requireNonNull(lowerBound);
            this.lowerBounds.add(lowerBound);
            this.isLowerBoundsMutated = true;
            return self();
        }

        /**
         * Generic types that provide keyword {@code extends} will have a lower bound defined.
         * Each lower bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param consumer consumer of builder for list of lower bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #lowerBounds()
         */
        public BUILDER addLowerBound(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.addLowerBound(builder.build());
            return self();
        }

        /**
         * Clear all upperBounds.
         *
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #upperBounds()
         */
        public BUILDER clearUpperBounds() {
            this.isUpperBoundsMutated = true;
            this.upperBounds.clear();
            return self();
        }

        /**
         * Generic types that provide keyword {@code super} will have an upper bound defined.
         * Upper bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param upperBounds list of upper bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #upperBounds()
         */
        public BUILDER upperBounds(List<? extends TypeName> upperBounds) {
            Objects.requireNonNull(upperBounds);
            this.isUpperBoundsMutated = true;
            this.upperBounds.clear();
            this.upperBounds.addAll(upperBounds);
            return self();
        }

        /**
         * Generic types that provide keyword {@code super} will have an upper bound defined.
         * Upper bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param upperBounds list of upper bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #upperBounds()
         */
        public BUILDER addUpperBounds(List<? extends TypeName> upperBounds) {
            Objects.requireNonNull(upperBounds);
            this.isUpperBoundsMutated = true;
            this.upperBounds.addAll(upperBounds);
            return self();
        }

        /**
         * Generic types that provide keyword {@code super} will have an upper bound defined.
         * Upper bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param upperBound add single list of upper bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #upperBounds()
         */
        public BUILDER addUpperBound(TypeName upperBound) {
            Objects.requireNonNull(upperBound);
            this.upperBounds.add(upperBound);
            this.isUpperBoundsMutated = true;
            return self();
        }

        /**
         * Generic types that provide keyword {@code super} will have an upper bound defined.
         * Upper bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @param consumer consumer of builder for list of upper bounds of this type
         * @return updated builder instance
         * @see io.helidon.common.types.TypeName#generic()
         * @see #upperBounds()
         */
        public BUILDER addUpperBound(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.addUpperBound(builder.build());
            return self();
        }

        /**
         * Clear existing value of componentType.
         *
         * @return updated builder instance
         * @see #componentType()
         */
        public BUILDER clearComponentType() {
            this.componentType = null;
            return self();
        }

        /**
         * Component type of array.
         *
         * @param componentType component type of array
         * @return updated builder instance
         * @see #componentType()
         */
        public BUILDER componentType(TypeName componentType) {
            Objects.requireNonNull(componentType);
            this.componentType = componentType;
            return self();
        }

        /**
         * Component type of array.
         *
         * @param consumer consumer of builder of component type of array
         * @return updated builder instance
         * @see #componentType()
         */
        public BUILDER componentType(Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.componentType(builder.build());
            return self();
        }

        /**
         * Component type of array.
         *
         * @param supplier supplier of component type of array
         * @return updated builder instance
         * @see #componentType()
         */
        public BUILDER componentType(Supplier<? extends TypeName> supplier) {
            Objects.requireNonNull(supplier);
            this.componentType(supplier.get());
            return self();
        }

        /**
         * Clear all annotations.
         *
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER clearAnnotations() {
            this.isAnnotationsMutated = true;
            this.annotations.clear();
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER annotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.isAnnotationsMutated = true;
            this.annotations.clear();
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotations the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotations(List<? extends Annotation> annotations) {
            Objects.requireNonNull(annotations);
            this.isAnnotationsMutated = true;
            this.annotations.addAll(annotations);
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param annotation add single the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Annotation annotation) {
            Objects.requireNonNull(annotation);
            this.annotations.add(annotation);
            this.isAnnotationsMutated = true;
            return self();
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @param consumer consumer of builder for the list of annotations declared on this element
         * @return updated builder instance
         * @see #annotations()
         */
        public BUILDER addAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addAnnotation(builder.build());
            return self();
        }

        /**
         * Clear all inheritedAnnotations.
         *
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER clearInheritedAnnotations() {
            this.isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER inheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            this.isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.clear();
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotations list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotations(List<? extends Annotation> inheritedAnnotations) {
            Objects.requireNonNull(inheritedAnnotations);
            this.isInheritedAnnotationsMutated = true;
            this.inheritedAnnotations.addAll(inheritedAnnotations);
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param inheritedAnnotation add single list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Annotation inheritedAnnotation) {
            Objects.requireNonNull(inheritedAnnotation);
            this.inheritedAnnotations.add(inheritedAnnotation);
            this.isInheritedAnnotationsMutated = true;
            return self();
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @param consumer consumer of builder for list of all meta annotations of this element
         * @return updated builder instance
         * @see #inheritedAnnotations()
         */
        public BUILDER addInheritedAnnotation(Consumer<Annotation.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = Annotation.builder();
            consumer.accept(builder);
            this.addInheritedAnnotation(builder.build());
            return self();
        }

        /**
         * Functions similar to {@link Class#getPackageName()}.
         *
         * @return the package name, never null
         */
        public String packageName() {
            return packageName;
        }

        /**
         * Functions similar to {@link Class#getSimpleName()}.
         *
         * @return the simple class name
         */
        public Optional<String> className() {
            return Optional.ofNullable(className);
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @return enclosing classes simple names
         */
        public List<String> enclosingNames() {
            return enclosingNames;
        }

        /**
         * Functions similar to {@link Class#isPrimitive()}.
         *
         * @return true if this type represents a primitive type
         */
        public boolean primitive() {
            return primitive;
        }

        /**
         * Functions similar to {@link Class#isArray()}.
         *
         * @return true if this type represents a primitive array []
         */
        public boolean array() {
            return array;
        }

        /**
         * If this is a representation of {@link io.helidon.common.types.TypeName#array()}, this method can identify that it
         * was declared as a vararg.
         * This may be used for method/constructor parameters (which is the only place this is supported in Java).
         *
         * @return whether an array is declared as a vararg
         */
        public boolean vararg() {
            return vararg;
        }

        /**
         * Indicates whether this type is using generics.
         *
         * @return used to represent a generic (e.g., "Optional&lt;CB&gt;")
         */
        public boolean generic() {
            return generic;
        }

        /**
         * Indicates whether this type is using wildcard generics.
         *
         * @return used to represent a wildcard (e.g., "? extends SomeType")
         */
        public boolean wildcard() {
            return wildcard;
        }

        /**
         * Returns the list of generic type arguments, or an empty list if no generics are in use.
         *
         * @return the type arguments of this type, if this type supports generics/parameterized type
         * @see #typeParameters()
         */
        public List<TypeName> typeArguments() {
            return typeArguments;
        }

        /**
         * Type parameters associated with the type arguments. The type argument list may be empty, even if this list is not,
         * for example in declaration of the top level type (as arguments are a function of usage of the type).
         * if {@link #typeArguments()} exist, this list MUST exist and have the same size and order (it maps the name to the type).
         *
         * @return type parameter names as declared on this type, or names that represent the {@link #typeArguments()}
         */
        public List<String> typeParameters() {
            return typeParameters;
        }

        /**
         * Generic types that provide keyword {@code extends} will have a lower bound defined.
         * Each lower bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @return list of lower bounds of this type
         * @see io.helidon.common.types.TypeName#generic()
         */
        public List<TypeName> lowerBounds() {
            return lowerBounds;
        }

        /**
         * Generic types that provide keyword {@code super} will have an upper bound defined.
         * Upper bound may be a real type, or another generic type.
         * <p>
         * This list may only have value if this is a generic type.
         *
         * @return list of upper bounds of this type
         * @see io.helidon.common.types.TypeName#generic()
         */
        public List<TypeName> upperBounds() {
            return upperBounds;
        }

        /**
         * Component type of array.
         *
         * @return component type of array
         */
        public Optional<TypeName> componentType() {
            return Optional.ofNullable(componentType);
        }

        /**
         * List of declared and known annotations for this element.
         * Note that "known" implies that the annotation is visible, which depends
         * upon the context in which it was build (such as the {@link java.lang.annotation.Retention of the annotation}).
         *
         * @return the list of annotations declared on this element
         */
        public List<Annotation> annotations() {
            return annotations;
        }

        /**
         * List of all inherited annotations for this element. Inherited annotations are annotations declared
         * on annotations of this element that are also marked as {@link java.lang.annotation.Inherited}.
         * <p>
         * The returned list does not contain {@link #annotations()}. If a meta-annotation is present on multiple
         * annotations, it will be returned once for each such declaration.
         * <p>
         * This method does not return annotations on super types or interfaces!
         *
         * @return list of all meta annotations of this element
         */
        public List<Annotation> inheritedAnnotations() {
            return inheritedAnnotations;
        }

        @Override
        public String toString() {
            return "TypeNameBuilder{"
                    + "packageName=" + packageName + ","
                    + "className=" + className + ","
                    + "enclosingNames=" + enclosingNames + ","
                    + "primitive=" + primitive + ","
                    + "array=" + array + ","
                    + "componentType=" + componentType
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
            new TypeNameSupport.Decorator().decorate(this);
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (className == null) {
                collector.fatal(getClass(), "Property \"className\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Component type of array.
         *
         * @param componentType component type of array
         * @return updated builder instance
         * @see #componentType()
         */
        @SuppressWarnings("unchecked")
        BUILDER componentType(Optional<? extends TypeName> componentType) {
            Objects.requireNonNull(componentType);
            this.componentType = componentType.map(TypeName.class::cast).orElse(this.componentType);
            return self();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypeNameImpl implements TypeName {

            private final boolean array;
            private final boolean generic;
            private final boolean primitive;
            private final boolean vararg;
            private final boolean wildcard;
            private final List<Annotation> annotations;
            private final List<Annotation> inheritedAnnotations;
            private final List<TypeName> lowerBounds;
            private final List<TypeName> typeArguments;
            private final List<TypeName> upperBounds;
            private final List<String> enclosingNames;
            private final List<String> typeParameters;
            private final Optional<TypeName> componentType;
            private final String className;
            private final String packageName;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected TypeNameImpl(TypeName.BuilderBase<?, ?> builder) {
                this.packageName = builder.packageName();
                this.className = builder.className().get();
                this.enclosingNames = List.copyOf(builder.enclosingNames());
                this.primitive = builder.primitive();
                this.array = builder.array();
                this.vararg = builder.vararg();
                this.generic = builder.generic();
                this.wildcard = builder.wildcard();
                this.typeArguments = List.copyOf(builder.typeArguments());
                this.typeParameters = List.copyOf(builder.typeParameters());
                this.lowerBounds = List.copyOf(builder.lowerBounds());
                this.upperBounds = List.copyOf(builder.upperBounds());
                this.componentType = builder.componentType().map(Function.identity());
                this.annotations = List.copyOf(builder.annotations());
                this.inheritedAnnotations = List.copyOf(builder.inheritedAnnotations());
            }

            @Override
            public String toString() {
                return TypeNameSupport.toString(this);
            }

            @Override
            public String packageName() {
                return packageName;
            }

            @Override
            public String className() {
                return className;
            }

            @Override
            public List<String> enclosingNames() {
                return enclosingNames;
            }

            @Override
            public boolean primitive() {
                return primitive;
            }

            @Override
            public boolean array() {
                return array;
            }

            @Override
            public boolean vararg() {
                return vararg;
            }

            @Override
            public boolean generic() {
                return generic;
            }

            @Override
            public boolean wildcard() {
                return wildcard;
            }

            @Override
            public List<TypeName> typeArguments() {
                return typeArguments;
            }

            @Override
            public List<String> typeParameters() {
                return typeParameters;
            }

            @Override
            public List<TypeName> lowerBounds() {
                return lowerBounds;
            }

            @Override
            public List<TypeName> upperBounds() {
                return upperBounds;
            }

            @Override
            public Optional<TypeName> componentType() {
                return componentType;
            }

            @Override
            public List<Annotation> annotations() {
                return annotations;
            }

            @Override
            public List<Annotation> inheritedAnnotations() {
                return inheritedAnnotations;
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof TypeName other)) {
                    return false;
                }
                return Objects.equals(packageName, other.packageName())
                    && Objects.equals(className, other.className())
                    && Objects.equals(enclosingNames, other.enclosingNames())
                    && primitive == other.primitive()
                    && array == other.array()
                    && Objects.equals(componentType, other.componentType());
            }

            @Override
            public int hashCode() {
                return Objects.hash(packageName, className, enclosingNames, primitive, array, componentType);
            }

        }

    }

    /**
     * Fluent API builder for {@link TypeName}.
     */
    class Builder extends TypeName.BuilderBase<TypeName.Builder, TypeName>
            implements io.helidon.common.Builder<TypeName.Builder, TypeName> {

        private Builder() {
        }

        @Override
        public TypeName buildPrototype() {
            preBuildPrototype();
            validatePrototype();
            return new TypeNameImpl(this);
        }

        @Override
        public TypeName build() {
            return buildPrototype();
        }

    }

}
