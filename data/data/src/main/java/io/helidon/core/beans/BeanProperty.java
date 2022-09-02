/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.core.beans;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

// FIXME: Just a placeholder
/**
 * Represents a bean property and associated annotation metadata.
 *
 * <p>A {@link BeanProperty} allows you to read the value of a property via {@link #get(Object)} or write to it via {@link #set(Object, Object)}, without using reflection.</p>
 *
 * <p>The annotations of a property can be inspected via the {@code #getAnnotationMetadata()} method.</p>
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author graemerocher
 * @since 1.1
 * See {@code BeanIntrospection}
 */
public interface BeanProperty<B, T> {

    /**
     * @return The declaring bean introspection.
     */
    //BeanIntrospection<B> getDeclaringBean();

    /**
     * Read the bean value.
     * @param bean The bean to read from
     * @return The value
     * @throws IllegalArgumentException If the bean instance if not of the correct type
     */
    T get(B bean);

    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param type The type
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    <T2> Optional<T2> get(B bean, Class<T2> type);


    /**
     * Read the value and try to convert it to the given type.
     * @param bean The bean
     * @param type The type
     * @param defaultValue The default value if conversion is not possible
     * @param <T2> The generic type
     * @return The value if conversion was possible.
     */
    <T2> T2 get(B bean, Class<T2> type, T2 defaultValue);

    /**
     * Write the bean value.
     * @param bean The bean
     * @param value The value to write
     * @throws IllegalArgumentException If either the bean type or value type are not correct
     */
    default void set(B bean, T value) {
        if (isReadOnly()) {
            throw new UnsupportedOperationException("Cannot write read-only property: "/* + getName()*/);
        } else {
            throw new UnsupportedOperationException("Write method unimplemented for property: "/* + getName()*/);
        }
    }

    /**
     * @return The property type.
     */
    Class<T> getType();

    /**
     * @return Whether the property is read-only
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * @return Whether the property is write-only
     */
    default boolean isWriteOnly() {
        return false;
    }

    /**
     * Whether the property can be written to and read from.
     *
     * @return True if it can.
     */
    default boolean isReadWrite() {
       return !isReadOnly() && !isWriteOnly();
    }

//    /**
//     * The declaring type of the property.
//     * @return The type
//     */
//    default Class<B> getDeclaringType() {
//        return getDeclaringBean().getBeanType();
//    }
}
