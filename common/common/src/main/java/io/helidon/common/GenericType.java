/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.Objects;

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
    private final Type type;
    private final Class<?> rawType;

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

        return new GenericType<>(genericType, GenericTypeUtil.rawClass(genericType));
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

    private GenericType(Type type, Class<?> rawType) {
        this.type = type;
        this.rawType = rawType;
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
        return rawType.equals(type);
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
        return false;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
