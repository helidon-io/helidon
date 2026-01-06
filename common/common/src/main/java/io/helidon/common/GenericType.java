/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
package io.helidon.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a full type including generics declaration, to avoid information loss due to type erasure.
 *
 * Supports in-line instantiation of objects that represent generic types with
 * actual type parameters. An object that represents any parameterized type may
 * be obtained by sub-classing {@code GenericType}. Alternatively, an object
 * representing a concrete parameterized type can be created using a
 * {@link GenericType#create(Type)} and manually specifying
 * the {@link #type()} actual (parameterized) type}.
 * <p>
 * For example:
 * </p>
 * <pre>
 *  GenericType&lt;List&lt;String&gt;&gt; stringListType = new GenericType&lt;List&lt;String&gt;&gt;() {};
 * </pre>
 * <p>
 * Or:
 * </p>
 * <pre>
 *  public class MyGenericType extends GenericType&lt;List&lt;String&gt;&gt; { ... }
 *
 *  ...
 *
 *  MyGenericType stringListType = new MyGenericType();
 * </pre>
 * <p>
 * Note that due to the Java type erasure limitations the parameterized type information
 * must be specified on a subclass, not just during the instance creation. For example,
 * the following case would throw an {@link IllegalArgumentException}:
 * </p>
 * <pre>
 *  public class MyGenericType&lt;T&gt; extends GenericType&lt;T&gt; { ... }
 *
 *  ...
 *
 *  // The type is only specified on instance, not in a sub-class
 *  MyGenericType&lt;List&lt;String&gt;&gt; stringListType =
 *          new MyGenericType&lt;List&lt;String&gt;&gt;();
 * </pre>
 *
 * @param <T> the generic type parameter
 */
public class GenericType<T> implements Type {
    /**
     * Generic type for String.
     */
    public static final GenericType<String> STRING = GenericType.create(String.class);
    /**
     * Generic type for Object.
     */
    public static final GenericType<Object> OBJECT = GenericType.create(Object.class);

    private final Type type;
    private final Class<?> rawType;
    private final boolean isClass;

    /**
     * Constructs a new generic type, using the provided generic type information and
     * deriving the class.
     *
     * @param genericType the generic type
     * @param <N> generic type of the returned GenericType
     * @return new type wrapping the provided type
     * @throws IllegalArgumentException if genericType is {@code null} or not an instance of
     *                                  {@code Class} or {@link ParameterizedType} whose raw
     *                                  type is an instance of {@code Class}.
     */
    public static <N> GenericType<N> create(Type genericType) throws IllegalArgumentException {
        Objects.requireNonNull(genericType);

        Type type = genericType instanceof GenericType<?> genType ? genType.type : genericType;
        return new GenericType<>(type, GenericTypeUtil.rawClass(type));
    }

    /**
     * Constructs a new generic type instance representing the given class.
     * @param <N> generic type of the returned GenericType
     * @param clazz the class to represent
     * @return new type wrapping the provided class
     */
    public static <N> GenericType<N> create(Class<N> clazz) {
        return new GenericType<>(clazz, clazz);
    }

    /**
     * Constructs a new generic type instance representing the class of the
     * given object.
     *
     * @param <N> generic type of the returned GenericType
     * @param object the object to derive the class of
     * @return new type wrapping the class of the provided object
     */
    public static <N> GenericType<N> create(N object) {
        return GenericType.<N>create(object.getClass());
    }

    /**
     * Create a new fluent builder instance.
     *
     * @return a new builder instance
     * @param <T> the generic type parameter
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    private GenericType(Type type, Class<?> rawType) {
        this.type = type;
        this.rawType = rawType;
        this.isClass = rawType.equals(type);
    }

    /**
     * Constructs a new generic type, deriving the generic type and class from
     * type parameter. Note that this constructor is protected, users should create
     * a (usually anonymous) subclass as shown above.
     *
     * @throws IllegalArgumentException in case the generic type parameter value is not
     *                                  provided by any of the subclasses.
     */
    protected GenericType() throws IllegalArgumentException {
        this.type = GenericTypeUtil.typeArgument(getClass(), GenericType.class);
        this.rawType = GenericTypeUtil.rawClass(type);
        this.isClass = rawType.equals(type);
    }

    /**
     * The type represented by this generic type instance.
     * <p>
     * For {@code new GenericType<List<String>>(){}}, this would return a {@link ParameterizedType}
     * for {@code java.util.List<java.lang.String>}.
     *
     * @return the actual type represented by this generic type instance.
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the object representing the class or interface that declared
     * the type represented by this generic type instance.
     * <p>
     * For {@code new GenericType<List<String>>(){}}, this would return an
     * {@code interface java.util.List}.
     *
     * @return the class or interface that declared the type represented by this
     *         generic type instance.
     */
    public Class<?> rawType() {
        return rawType;
    }

    /**
     * Whether this generic type represents a simple class with no generic information.
     *
     * @return true if this is a class, false if this is a generic type (e.g. returns true for {@code List}, false for {@code
     *         List<String>}
     */
    public boolean isClass() {
        return isClass;
    }

    /**
     * Casts the parameter to the type of this generic type.
     * This is a utility method to use in stream processing etc.
     *
     * @param object instance to cast
     * @return typed instance
     * @throws ClassCastException in case the object is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public T cast(Object object) throws ClassCastException {
        return (T) object;
    }

    @Override
    public String getTypeName() {
        return type().toString();
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof GenericType) {
            return ((GenericType<?>) obj).type.equals(this.type);
        }
        if (obj instanceof Type t) {
            return t.equals(type);
        }
        return false;
    }

    @Override
    public String toString() {
        return type.toString();
    }

    /**
     * Builder of the Generic type.
     *
     * @param <T> the generic type parameter
     */
    public static final class Builder<T> implements io.helidon.common.Builder<Builder<T>, GenericType<T>> {

        private Class<?> baseType;
        private final List<Type> genericParameters = new ArrayList<>();

        private Builder() {
        }

        /**
         * Builds the GenericType instance.
         *
         * @return the built GenericType
         */
        public GenericType<T> build() {
            if (baseType == null) {
                throw new IllegalStateException("Base type has to be set");
            }
            if (genericParameters.isEmpty()) {
                return new GenericType<>(baseType, baseType);
            }
            Type[] genericParametersArray = genericParameters.toArray(new Type[0]);
            HelidonParameterizedType parameterizedType = new HelidonParameterizedType(baseType, genericParametersArray);
            return new GenericType<>(parameterizedType, baseType);
        }

        /**
         * Sets the base type for this generic type.
         *
         * @param baseType the base class type
         * @return this builder
         */
        public Builder<T> baseType(Class<?> baseType) {
            Objects.requireNonNull(baseType);
            this.baseType = baseType;
            return this;
        }

        /**
         * Adds a generic parameter using the provided GenericType.
         *
         * @param genericParameter the generic parameter to add
         * @return this builder
         */
        public Builder<T> addGenericParameter(GenericType<?> genericParameter) {
            Objects.requireNonNull(genericParameter);
            this.genericParameters.add(genericParameter.type());
            return this;
        }

        /**
         * Adds a generic parameter using the provided Type.
         *
         * @param genericParameter the generic parameter to add
         * @return this builder
         */
        public Builder<T> addGenericParameter(Type genericParameter) {
            Objects.requireNonNull(genericParameter);
            this.genericParameters.add(genericParameter);
            return this;
        }

        /**
         * Adds a generic parameter by building a nested GenericType using the provided consumer.
         *
         * @param consumer the consumer to configure the nested builder
         * @return this builder
         */
        public Builder<T> addGenericParameter(Consumer<Builder<T>> consumer) {
            Objects.requireNonNull(consumer);
            Builder<T> builder = new Builder<T>();
            consumer.accept(builder);
            this.genericParameters.add(builder.build());
            return this;
        }

    }

}
