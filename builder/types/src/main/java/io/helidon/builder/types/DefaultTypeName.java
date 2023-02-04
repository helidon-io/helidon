/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation for {@link io.helidon.builder.types.TypeName}.
 */
public class DefaultTypeName implements TypeName {
    private final String packageName;
    private final String className;
    private final boolean primitive;
    private final boolean array;
    private final boolean wildcard;
    private final boolean generic;
    private final List<TypeName> typeArguments;

    /**
     * Ctor.
     *
     * @param b the builder
     * @see #builder()
     */
    protected DefaultTypeName(Builder b) {
        this.packageName = b.packageName;
        this.className = b.className;
        this.primitive = b.primitive;
        this.array = b.array;
        this.wildcard = b.wildcard;
        this.generic = b.generic;
        this.typeArguments = Collections.unmodifiableList(b.typeArguments);
    }

    /**
     * @return the {@link #fqName()}.
     */
    @Override
    public String toString() {
        return fqName();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeName)) {
            return false;
        }
        TypeName other = (TypeName) o;
        return Objects.equals(name(), other.name())
                && primitive == other.primitive()
                && array == other.array();
    }

    @Override
    public int compareTo(TypeName o) {
        return name().compareTo(o.name());
    }

    /**
     * Creates a type name from a package name and simple class name.
     *
     * @param packageName the package name
     * @param className   the simple class type name
     * @return the TypeName for the provided package and class names
     */
    public static DefaultTypeName create(String packageName, String className) {
        return DefaultTypeName.builder().packageName(packageName).className(className).build();
    }

    /**
     * Creates a type name from a class.
     *
     * @param classType the class type
     * @return the TypeName for the provided class type
     */
    public static DefaultTypeName create(Class<?> classType) {
        return builder().type(classType).build();
    }

    /**
     * Creates a type name from a generic alias type name.
     *
     * @param genericAliasTypeName the generic alias type name
     * @return the TypeName for the provided type name
     */
    public static DefaultTypeName createFromGenericDeclaration(String genericAliasTypeName) {
        return builder()
                .generic(true)
                .className(Objects.requireNonNull(genericAliasTypeName))
                .build();
    }

    /**
     * Creates a type name from a fully qualified class name.
     *
     * @param typeName the FQN of the class type
     * @return the TypeName for the provided type name
     */
    public static DefaultTypeName createFromTypeName(String typeName) {
        Objects.requireNonNull(typeName);
        if (typeName.startsWith("?")) {
            if (typeName.startsWith("? extends ")) {
                return createFromTypeName(typeName.substring(10).trim())
                        .toBuilder()
                        .wildcard(true)
                        .build();
            } else {
                return DefaultTypeName.builder()
                        .type(Object.class)
                        .wildcard(true)
                        .build();
            }
        }

        // a.b.c.SomeClass
        // a.b.c.SomeClass.InnerClass.Builder
        String className = typeName;
        List<String> packageElements = new ArrayList<>();

        while (true) {
            if (Character.isUpperCase(className.charAt(0))) {
                break;
            }
            int dot = className.indexOf('.');
            if (dot == -1) {
                // no more dots, we have the class name
                break;
            }
            packageElements.add(className.substring(0, dot));
            className = className.substring(dot + 1);
        }

        if (packageElements.isEmpty()) {
            return DefaultTypeName.builder()
                    .className(typeName)
                    .build();
        }

        String packageName = String.join(".", packageElements);
        return create(packageName, className);
    }

    /**
     * Given a typeName X, will return an typeName of "? extends X".
     *
     * @param typeName  the typeName
     * @return the wildcard extension of the given typeName
     */
    public static TypeName createExtendsTypeName(TypeName typeName) {
        return toBuilder(typeName)
                .wildcard(true)
                .build();
    }

    /**
     * Throws an exception if the provided type name is not fully qualified, having a package and class name representation.
     *
     * @param name the type name to check
     * @throws java.lang.IllegalStateException if the name is invalid
     */
    public static void ensureIsFQN(TypeName name) {
        if (!isFQN(name)) {
            throw new IllegalStateException("needs to be a fully qualified name: " + name);
        }
    }

    /**
     * Returns true if the provided type name is fully qualified, having a package and class name representation.
     *
     * @param name the type name to check
     * @return true if the provided name is fully qualified
     */
    public static boolean isFQN(TypeName name) {
        return !Objects.requireNonNull(name.packageName()).isBlank()
                && !Objects.requireNonNull(name.className()).isBlank()
                && !Objects.requireNonNull(name.name()).isBlank();
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
    public List<TypeName> typeArguments() {
        return typeArguments;
    }

    @Override
    public String name() {
        return calcName();
    }

    @Override
    public String declaredName() {
        return array() ? (name() + "[]") : name();
    }

    @Override
    public String fqName() {
        return calcFQName();
    }

    /**
     * Calculates the name - this is lazily deferred until referenced in {@link #name}.
     *
     * @return the name
     */
    protected String calcName() {
        return (primitive || packageName().isEmpty())
                ? className() : packageName() + "." + className();
    }

    /**
     * Calculates the fully qualified name - this is lazily deferred until referenced in {@link #fqName()}.
     *
     * @return the fully qualified name
     */
    protected String calcFQName() {
        String name = name();
        boolean isObject = Object.class.getName().equals(name);
        StringBuilder nameBuilder = (isObject)
                ? new StringBuilder(wildcard() ? "?" : name)
                : new StringBuilder(wildcard() ? "? extends " + name : name);

        if (null != typeArguments && !typeArguments.isEmpty()) {
            nameBuilder.append("<");
            int i = 0;
            for (TypeName param : typeArguments) {
                if (i > 0) {
                    nameBuilder.append(", ");
                }
                nameBuilder.append(param.fqName());
                i++;
            }
            nameBuilder.append(">");
        }

        if (array()) {
            nameBuilder.append("[]");
        }

        return nameBuilder.toString();
    }


    /**
     * Creates a builder for {@link io.helidon.builder.types.TypeName}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with a value passed.
     *
     * @param val the value
     * @return a fluent builder
     */
    public static Builder toBuilder(TypeName val) {
        return new Builder(val);
    }

    /**
     * Creates a builder initialized with the current values.
     *
     * @return a fluent builder
     */
    public Builder toBuilder() {
        return toBuilder(this);
    }


    /**
     * The fluent builder.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DefaultTypeName> {
        private final List<TypeName> typeArguments = new ArrayList<>();

        private String packageName;
        private String className;
        private boolean primitive;
        private boolean array;
        private boolean wildcard;
        private boolean generic;

        /**
         * Default ctor.
         */
        protected Builder() {
        }

        /**
         * Ctor taking the typeName for initialization.
         *
         * @param val   the typeName
         */
        protected Builder(TypeName val) {
            copyFrom(val);
        }

        /**
         * Builds the instance.
         *
         * @return the built instance
         */
        public DefaultTypeName build() {
            Objects.requireNonNull(className, "Class name must be specified");
            packageName = packageName == null ? "" : packageName;

            return new DefaultTypeName(this);
        }

        /**
         * Copy from an existing typeName.
         *
         * @param val   the typeName to copy
         * @return the fluent builder
         */
        protected Builder copyFrom(TypeName val) {
            this.packageName = val.packageName();
            this.className = val.className();
            this.primitive = val.primitive();
            this.array = val.array();
            this.wildcard = val.wildcard();
            this.generic = val.generic();
            this.typeArguments.addAll(val.typeArguments());
            return this;
        }

        /**
         * Set the package name.
         *
         * @param val   the package name
         * @return this fluent builder
         */
        public Builder packageName(String val) {
            Objects.requireNonNull(val);
            this.packageName = val;
            return this;
        }

        /**
         * Set the simple class name.
         *
         * @param val  the simple class name
         * @return the fluent builder
         */
        public Builder className(String val) {
            Objects.requireNonNull(val);
            this.className = val;
            return this;
        }

        /**
         * Sets the package and class name, as well as whether it is primitive or an array.
         *
         * @param classType  the class
         * @return the fluent builder
         */
        public Builder type(Class<?> classType) {
            Objects.requireNonNull(classType);
            Class<?> componentType = classType.isArray() ? classType.getComponentType() : classType;
            packageName(componentType.getPackageName());
            className(componentType.getSimpleName());
            primitive(componentType.isPrimitive());
            return array(classType.isArray());
        }

        /**
         * Sets the array flag for this type.
         *
         * @param val   the array flag value
         * @return the fluent builder
         */
        public Builder array(boolean val) {
            Objects.requireNonNull(val);
            this.array = val;
            return this;
        }

        /**
         * Sets the primitive flag for this type.
         *
         * @param val   the primitive flag value
         * @return the fluent builder
         */
        public Builder primitive(boolean val) {
            Objects.requireNonNull(val);
            this.primitive = val;
            return this;
        }

        /**
         * Sets the generic flag for this type.
         *
         * @param val   the generic flag value
         * @return the fluent builder
         */
        public Builder generic(boolean val) {
            Objects.requireNonNull(val);
            this.generic = val;
            return this;
        }

        /**
         * Sets the wildcard flag for this type, and conditionally the generic flag if the value passed is true.
         *
         * @param val   the array flag value
         * @return the fluent builder
         */
        public Builder wildcard(boolean val) {
            Objects.requireNonNull(val);
            this.wildcard = val;
            if (val) {
                this.generic = true;
            }
            return this;
        }

        /**
         * Sets the generic type arguments to the collection passed, and if not empty will set the generic flag to true.
         *
         * @param val   the generic type arguments
         * @return the fluent builder
         */
        public Builder typeArguments(Collection<TypeName> val) {
            Objects.requireNonNull(val);
            this.typeArguments.clear();
            this.typeArguments.addAll(val);
            return !val.isEmpty() ? generic(true) : this;
        }
    }

}
