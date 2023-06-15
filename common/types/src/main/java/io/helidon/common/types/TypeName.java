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

package io.helidon.common.types;

import java.util.Objects;

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
 * <li>{@link #declaredName()} and {@link #fqName()}.</li>
 * </ul>
 *
 * @see #builder()
 */
@io.helidon.common.Generated(value = "io.helidon.builder.processor.BlueprintProcessor",
                             trigger = "io.helidon.common.types.TypeNameBlueprint")
public interface TypeName
        extends TypeNameBlueprint, io.helidon.builder.api.Prototype, Comparable<TypeName> {
    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(TypeName instance) {
        return TypeName.builder().from(instance);
    }

    /**
     *Create a type name from a type (such as class).
     *
     * @param type the type
     * @return type name for the provided type
     */
    static TypeName create(java.lang.reflect.Type type) {
        return TypeNameSupport.create(type);
    }

    /**
     *Creates a type name from a fully qualified class name.
     *
     * @param typeName the FQN of the class type
     * @return the TypeName for the provided type name
     */
    static TypeName create(String typeName) {
        return TypeNameSupport.create(typeName);
    }

    /**
     *Creates a type name from a generic alias type name.
     *
     * @param genericAliasTypeName the generic alias type name
     * @return the TypeName for the provided type name
     */
    static TypeName createFromGenericDeclaration(String genericAliasTypeName) {
        return TypeNameSupport.createFromGenericDeclaration(genericAliasTypeName);
    }

    /**
     *Return the boxed equivalent of this type.
     *If this is not a primitive type, returns this instance.
     *
     *@return boxed type for this type, or this type if not primitive
     */
    TypeName boxed();

    /**
     *The base generic type name, stripped of any {@link TypeName#typeArguments()}.
     *This is equivalent to the type name represented by {@link TypeName#name()}.
     *
     *@return based generic type name
     */
    TypeName genericTypeName();

    /**
     * Fluent API builder base for {@link io.helidon.common.types.TypeName}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     * @param <PROTOTYPE> type of the prototype interface that would be built by {@link #buildPrototype()}
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>, PROTOTYPE extends TypeName>
            implements io.helidon.builder.api.Prototype.Builder<BUILDER, PROTOTYPE>, TypeName {
        private final java.util.List<String> enclosingNames = new java.util.ArrayList<>();
        private final java.util.List<TypeName> typeArguments = new java.util.ArrayList<>();
        private String packageName = "";
        private String className;
        private boolean primitive = false;
        private boolean array = false;
        private boolean generic = false;
        private boolean wildcard = false;

        /**
         * Protected to support extensibility.
         *
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(TypeName prototype) {
            packageName(prototype.packageName());
            className(prototype.className());
            addEnclosingNames(prototype.enclosingNames());
            primitive(prototype.primitive());
            array(prototype.array());
            generic(prototype.generic());
            wildcard(prototype.wildcard());
            addTypeArguments(prototype.typeArguments());
            return me();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?, ?> builder) {
            if (builder.packageName() != null) {
                packageName(builder.packageName());
            }
            if (builder.className() != null) {
                className(builder.className());
            }
            addEnclosingNames(builder.enclosingNames());
            primitive(builder.primitive());
            array(builder.array());
            generic(builder.generic());
            wildcard(builder.wildcard());
            addTypeArguments(builder.typeArguments());
            return me();
        }

        /**
         * Handles providers and interceptors.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (className == null) {
                collector.fatal(getClass(), "Property \"class-name\" is required, but not set");
            }
            collector.collect().checkValid();
        }

        @Override
        public int compareTo(TypeName o) {
            return TypeNameSupport.compareTo(this, o);
        }

        /**
         *Return the boxed equivalent of this type.
         *If this is not a primitive type, returns this instance.
         *
         *@return boxed type for this type, or this type if not primitive
         */
        @Override
        public TypeName boxed() {
            return TypeNameSupport.boxed(this);
        }

        @Override
        public String toString() {
            return TypeNameSupport.toString(this);
        }

        @Override
        public String name() {
            return TypeNameSupport.name(this);
        }

        /**
         *The base generic type name, stripped of any {@link TypeName#typeArguments()}.
         *This is equivalent to the type name represented by {@link TypeName#name()}.
         *
         *@return based generic type name
         */
        @Override
        public TypeName genericTypeName() {
            return TypeNameSupport.genericTypeName(this);
        }

        @Override
        public String fqName() {
            return TypeNameSupport.fqName(this);
        }

        /**
         *Update builder from the provided type.
         *
         *@param type type to get information (package name, class name, primitive, array)
         *@return updated builder instance
         */
        public BUILDER type(java.lang.reflect.Type type) {
            TypeNameSupport.type(this, type);
            return me();
        }

        /**
         * Functions the same as {@link Class#getPackageName()}.
         *
         * @param packageName the package name, never null
         * @return updated builder instance
         * @see #packageName()
         */
        public BUILDER packageName(String packageName) {
            Objects.requireNonNull(packageName);
            this.packageName = packageName;
            return me();
        }

        /**
         * Functions the same as {@link Class#getSimpleName()}.
         *
         * @param className the simple class name
         * @return updated builder instance
         * @see #className()
         */
        public BUILDER className(String className) {
            Objects.requireNonNull(className);
            this.className = className;
            return me();
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
        public BUILDER enclosingNames(java.util.List<? extends String> enclosingNames) {
            Objects.requireNonNull(enclosingNames);
            this.enclosingNames.clear();
            this.enclosingNames.addAll(enclosingNames);
            return me();
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
        public BUILDER addEnclosingNames(java.util.List<? extends String> enclosingNames) {
            Objects.requireNonNull(enclosingNames);
            this.enclosingNames.addAll(enclosingNames);
            return me();
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @param enclosingName enclosing classes simple names
         * @return updated builder instance
         * @see #enclosingNames()
         */
        public BUILDER addEnclosingName(String enclosingName) {
            Objects.requireNonNull(enclosingName);
            this.enclosingNames.add(enclosingName);
            return me();
        }

        /**
         * Functions the same as {@link Class#isPrimitive()}.
         *
         * @param primitive true if this type represents a primitive type
         * @return updated builder instance
         * @see #primitive()
         */
        public BUILDER primitive(boolean primitive) {
            this.primitive = primitive;
            return me();
        }

        /**
         * Functions the same as {@link Class#isArray()}.
         *
         * @param array true if this type represents a primitive array []
         * @return updated builder instance
         * @see #array()
         */
        public BUILDER array(boolean array) {
            this.array = array;
            return me();
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
            return me();
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
            return me();
        }

        /**
         * Returns the list of generic type parameters, or an empty list if no generics are in use.
         *
         * @param typeArguments the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeArguments()
         */
        public BUILDER typeArguments(java.util.List<? extends TypeName> typeArguments) {
            Objects.requireNonNull(typeArguments);
            this.typeArguments.clear();
            this.typeArguments.addAll(typeArguments);
            return me();
        }

        /**
         * Returns the list of generic type parameters, or an empty list if no generics are in use.
         *
         * @param typeArguments the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeArguments()
         */
        public BUILDER addTypeArguments(java.util.List<? extends TypeName> typeArguments) {
            Objects.requireNonNull(typeArguments);
            this.typeArguments.addAll(typeArguments);
            return me();
        }

        /**
         * Returns the list of generic type parameters, or an empty list if no generics are in use.
         *
         * @param typeArgument the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeArguments()
         */
        public BUILDER addTypeArgument(TypeName typeArgument) {
            Objects.requireNonNull(typeArgument);
            this.typeArguments.add(typeArgument);
            return me();
        }

        /**
         * Returns the list of generic type parameters, or an empty list if no generics are in use.
         *
         * @param consumer the type arguments of this type, if this type supports generics/parameterized type
         * @return updated builder instance
         * @see #typeArguments()
         */
        public BUILDER addTypeArgument(java.util.function.Consumer<TypeName.Builder> consumer) {
            Objects.requireNonNull(consumer);
            var builder = TypeName.builder();
            consumer.accept(builder);
            this.typeArguments.add(builder.build());
            return me();
        }

        /**
         * Functions the same as {@link Class#getPackageName()}.
         *
         * @return the package name
         */
        @Override
        public String packageName() {
            return packageName;
        }

        /**
         * Functions the same as {@link Class#getSimpleName()}.
         *
         * @return the class name
         */
        @Override
        public String className() {
            return className;
        }

        /**
         * Simple names of enclosing classes (if any exist).
         * For example for type {@code io.helidon.example.Type$NestOne$NestTwo}, this would return
         * a list of {@code Type, NestOne}.
         *
         * @return the enclosing names
         */
        @Override
        public java.util.List<String> enclosingNames() {
            return enclosingNames;
        }

        /**
         * Functions the same as {@link Class#isPrimitive()}.
         *
         * @return the primitive
         */
        @Override
        public boolean primitive() {
            return primitive;
        }

        /**
         * Functions the same as {@link Class#isArray()}.
         *
         * @return the array
         */
        @Override
        public boolean array() {
            return array;
        }

        /**
         * Indicates whether this type is using generics.
         *
         * @return the generic
         */
        @Override
        public boolean generic() {
            return generic;
        }

        /**
         * Indicates whether this type is using wildcard generics.
         *
         * @return the wildcard
         */
        @Override
        public boolean wildcard() {
            return wildcard;
        }

        /**
         * Returns the list of generic type parameters, or an empty list if no generics are in use.
         *
         * @return the type arguments
         */
        @Override
        public java.util.List<TypeName> typeArguments() {
            return typeArguments;
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class TypeNameImpl implements TypeName {
            private final String packageName;
            private final String className;
            private final java.util.List<String> enclosingNames;
            private final boolean primitive;
            private final boolean array;
            private final boolean generic;
            private final boolean wildcard;
            private final java.util.List<TypeName> typeArguments;

            /**
             * Create an instance providing a builder.
             * @param builder extending builder base of this prototype
             */
            protected TypeNameImpl(BuilderBase<?, ?> builder) {
                this.packageName = builder.packageName();
                this.className = builder.className();
                this.enclosingNames = java.util.List.copyOf(builder.enclosingNames());
                this.primitive = builder.primitive();
                this.array = builder.array();
                this.generic = builder.generic();
                this.wildcard = builder.wildcard();
                this.typeArguments = java.util.List.copyOf(builder.typeArguments());
            }

            @Override
            public int compareTo(TypeName o) {
                return TypeNameSupport.compareTo(this, o);
            }

            @Override
            public TypeName boxed() {
                return TypeNameSupport.boxed(this);
            }

            @Override
            public String toString() {
                return TypeNameSupport.toString(this);
            }

            @Override
            public String name() {
                return TypeNameSupport.name(this);
            }

            @Override
            public TypeName genericTypeName() {
                return TypeNameSupport.genericTypeName(this);
            }

            @Override
            public String fqName() {
                return TypeNameSupport.fqName(this);
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
            public java.util.List<String> enclosingNames() {
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
            public boolean generic() {
                return generic;
            }

            @Override
            public boolean wildcard() {
                return wildcard;
            }

            @Override
            public java.util.List<TypeName> typeArguments() {
                return typeArguments;
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
                        && array == other.array();
            }

            @Override
            public int hashCode() {
                return Objects.hash(packageName, className, enclosingNames, primitive, array);
            }
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.common.types.TypeName}.
     */
    class Builder extends BuilderBase<Builder, TypeName> implements io.helidon.common.Builder<Builder, TypeName> {
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
