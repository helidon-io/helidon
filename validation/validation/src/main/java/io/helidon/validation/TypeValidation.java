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

package io.helidon.validation;

import io.helidon.service.registry.Service;

/**
 * A validator service.
 * <p>
 * Validator allows programmatic validation of instances, properties and values.
 */
@Service.Contract
public interface TypeValidation {
    /**
     * Validate an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     *
     * @param type   type annotated with {@link io.helidon.validation.Validation.Validated} to use for validation of the
     *               provided object
     * @param object object instance to validate
     * @param <T>    type of the validated object, i.e. the type annotated with {@link io.helidon.validation.Validation.Validated}
     * @return validator response
     * @throws java.lang.IllegalArgumentException in case the type is not validated
     */
    <T> ValidationResponse validate(Class<T> type,
                                    T object);

    /**
     * Validate an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     *
     * @param type   type annotated with {@link io.helidon.validation.Validation.Validated} to use for validation of the
     *               provided object
     * @param object object instance to validate
     * @param <T>    type of the validated object, i.e. the type annotated with {@link io.helidon.validation.Validation.Validated}
     * @throws java.lang.IllegalArgumentException        in case the type is not validated
     * @throws io.helidon.validation.ValidationException in case the type is not validated or validation fails
     */
    <T> void check(Class<T> type,
                   T object);

    /**
     * Validate a specific property of an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param type         type annotated with {@link io.helidon.validation.Validation.Validated} to use for validation of the
     *                     provided object
     * @param object       object instance to validate
     * @param propertyName name of the property
     *                     @param <T> type of the validated object, i.e. the type annotated with
     *                     {@link io.helidon.validation.Validation.Validated}
     * @return validator response
     * @throws java.lang.IllegalArgumentException in case the type is not validated
     */
    <T> ValidationResponse validate(Class<T> type,
                                    T object,
                                    String propertyName);

    /**
     * Validate a specific property of an object that is annotated with {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param type         type annotated with {@link io.helidon.validation.Validation.Validated} to use for validation of the
     *                     provided object
     * @param object       object instance to validate
     * @param propertyName name of the property
     *                     @param <T> type of the validated object, i.e. the type annotated with
     *                     {@link io.helidon.validation.Validation.Validated}
     * @throws java.lang.IllegalArgumentException        in case the type is not validated
     * @throws io.helidon.validation.ValidationException in case the type is not validated or validation fails
     */
    <T> void check(Class<T> type,
                   T object,
                   String propertyName);

    /**
     * Validate a value against a specific property of an object that is annotated with
     * {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param type         type annotated with {@link io.helidon.validation.Validation.Validated}
     * @param propertyName name of the property
     * @param value        value to check
     * @return validator response
     * @throws java.lang.IllegalArgumentException in case the type is not validated
     */
    ValidationResponse validateProperty(Class<?> type,
                                        String propertyName,
                                        Object value);

    /**
     * Validate a value against a specific property of an object that is annotated with
     * {@link io.helidon.validation.Validation.Validated}.
     * <p>
     * A property is considered to be one of the following:
     * <ul>
     *     <li>A record component with constraint annotation(s)</li>
     *     <li>A method with constraint annotation(s) that matches getter pattern - non-void return type, no parameters</li>
     *     <li>Non-private field with constraint annotation(s)</li>
     * </ul>
     *
     * @param type         type annotated with {@link io.helidon.validation.Validation.Validated}
     * @param propertyName name of the property
     * @param value        value to check
     * @throws java.lang.IllegalArgumentException        in case the type is not validated
     * @throws io.helidon.validation.ValidationException in case the type is not validated or validation fails
     */
    void checkProperty(Class<?> type,
                       String propertyName,
                       Object value);
}
