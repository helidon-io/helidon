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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

import java.util.function.Supplier;

/**
 * A runtime representation of {@link PersistentProperty}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The owner type
 */
public class RuntimePersistentProperty<T> implements PersistentProperty {
    public static final RuntimePersistentProperty<Object>[] EMPTY_PROPERTY_ARRAY = new RuntimePersistentProperty[0];
    private final RuntimePersistentEntity<T> owner;
    private final BeanProperty<T, ?> property;
    private final Class<?> type;
    private final DataType dataType;
    private final boolean constructorArg;
    private final Argument<?> argument;
    private final Supplier<AttributeConverter<Object, Object>> converter;
    private String persistedName;

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     * @param constructorArg whether it is a constructor arg
     */
    RuntimePersistentProperty(RuntimePersistentEntity<T> owner, BeanProperty<T, ?> property, boolean constructorArg) {
        this.owner = owner;
        this.property = property;
        this.type = ReflectionUtils.getWrapperType(property.getType());
        this.dataType = PersistentProperty.super.getDataType();
        this.constructorArg = constructorArg;
        this.argument = property.asArgument();
        this.converter = property.classValue(MappedProperty.class, "converter")
                .map(converter -> SupplierUtil.memoized(() -> owner.resolveConverter(converter)))
                .orElse(null);
    }

    /**
     * @return The argument for this property.
     */
    public Argument<?> getArgument() {
        return argument;
    }

    @Override
    public boolean isConstructorArgument() {
        return constructorArg;
    }

    @Override
    public final boolean isOptional() {
        return property.isNullable();
    }

    @Override
    public boolean isEnum() {
        return type.isEnum();
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    @Override
    public boolean isReadOnly() {
        return property.isReadOnly();
    }

    /**
     * @return The property type, unwrapped if primitive
     */
    public @NonNull Class<?> getType() {
        return type;
    }

    @NonNull
    @Override
    public String getName() {
        return property.getName();
    }

    @NonNull
    @Override
    public String getTypeName() {
        return property.getType().getName();
    }

    @NonNull
    @Override
    public RuntimePersistentEntity<T> getOwner() {
        return owner;
    }

    @Override
    public boolean isAssignable(@NonNull String type) {
        throw new UnsupportedOperationException("Use isAssignable(Class) instead");
    }

    @Override
    public boolean isAssignable(@NonNull Class<?> type) {
        return type.isAssignableFrom(getProperty().getType());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return property.getAnnotationMetadata();
    }

    /**
     * @return The backing bean property
     */
    public BeanProperty<T, ?> getProperty() {
        return property;
    }

    @Override
    public AttributeConverter<Object, Object> getConverter() {
        if (converter == null) {
            return null;
        }
        return converter.get();
    }

    @NonNull
    @Override
    public String getPersistedName() {
        if (persistedName == null) {
            persistedName = owner.getNamingStrategy().mappedName(this);
        }
        return this.persistedName;
    }

    @Override
    public String toString() {
        return getOwner().getSimpleName() + "." + getName();
    }
}
