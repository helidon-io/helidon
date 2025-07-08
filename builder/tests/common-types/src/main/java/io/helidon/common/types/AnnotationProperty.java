/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.Optional;

/**
 * A property of an annotation.
 * A property has a value. It may also contain a reference to a constant that defines its value, for example
 * when generating the annotation into source code.
 * <p>
 * Support types are defined on {@link io.helidon.common.types.Annotation}.
 */
public interface AnnotationProperty {
    /**
     * Create a new annotation property.
     *
     * @param value value of the property, must be one of the supported types, see
     *              {@link Annotation}; must not be an instance of this class
     * @return a new annotation property
     * @see io.helidon.common.types.Annotation.BuilderBase#putValue(String, Object)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static AnnotationProperty create(Object value) {
        Objects.requireNonNull(value);

        if (value instanceof AnnotationProperty) {
            throw new IllegalArgumentException("Cannot use an existing annotation property to create a new one."
                                                       + ", value: " + value);
        }
        if (value instanceof EnumValue ev) {
            return new AnnotationPropertyImpl(value, ev);
        }
        if (value instanceof Enum en) {
            return new AnnotationPropertyImpl(value, EnumValue.create(en.getDeclaringClass(), en));
        }
        return new AnnotationPropertyImpl(value);
    }

    /**
     * Create a new annotation property with a constant value. Constant value can be used when generating code.
     *
     * @param value        value of the property, must be one of the supported types, see
     *                     {@link Annotation}; must not be an instance of this class
     * @param constantType type of the class that holds the referenced constant
     * @param constantName name of the constant (i.e. an accessible static final field)
     * @return a new annotation property
     * @see io.helidon.common.types.Annotation.BuilderBase#putValue(String, Object)
     */
    static AnnotationProperty create(Object value, TypeName constantType, String constantName) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(constantType);
        Objects.requireNonNull(constantName);

        if (value instanceof AnnotationProperty) {
            throw new IllegalArgumentException("Cannot use an existing annotation property to create a new one."
                                                       + " Value: " + value);
        }
        if (value instanceof EnumValue ev) {
            if (!ev.type().equals(constantType) || !ev.name().equals(constantName)) {
                throw new IllegalArgumentException("Inconsistent annotation value vs. its constant value, an enum"
                                                           + " must use an enum constant. Enum: " + ev + ","
                                                           + " Constant: " + constantType.fqName() + "." + constantName);
            }
        }
        return new AnnotationPropertyImpl(value, new EnumValueImpl(constantType, constantName));
    }

    /**
     * Create a new enum property.
     *
     * @param type  enum type
     * @param value enum value constant
     * @param <T>   type of the enum
     * @return new enum value
     */
    static <T extends Enum<T>> AnnotationProperty create(Class<T> type, T value) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(value);

        return create(EnumValue.create(type, value));
    }

    /**
     * Value of the property, never {@code null}.
     *
     * @return value of the property
     */
    Object value();

    /**
     * Constant value (i.e. a class and an accessible constant on it) that should be used if this annotation is generated
     * into source code. In case this is an enum type, the constant value will return the appropriate
     * {@link EnumValue}.
     *
     * @return constant value if defined
     */
    Optional<ConstantValue> constantValue();

    /**
     * A reference to a constant, that can be used in generated annotation code.
     */
    interface ConstantValue {
        /**
         * Type name that declares the constant.
         *
         * @return type name
         */
        TypeName type();

        /**
         * Name of the constant.
         *
         * @return constant name
         */
        String name();
    }
}

