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

package io.helidon.validation.spi;

import io.helidon.service.registry.Service;
import io.helidon.validation.ValidationContext;

/**
 * A (usually generated) validator for a specific type.
 * The validator implements this interface and must be named with the fully qualified class name of the annotated type.
 * <p>
 * The generation is triggered by the {@link io.helidon.validation.Validation.Validated} annotation.
 *
 * @param <T> type of the validated object
 */
@Service.Contract
public interface TypeValidator<T> {
    /**
     * Validation the object instance that it is valid.
     *
     * @param context  validation context
     * @param instance instance to validate
     * @see io.helidon.validation.ValidationContext#response()
     * @see io.helidon.validation.ValidationContext#throwOnFailure()
     */
    void check(ValidationContext context, T instance);

    /**
     * Validation a single property only.
     *
     * @param context      validation context
     * @param instance     instance to validate
     * @param propertyName name of the property to validate
     * @see io.helidon.validation.ValidationContext#response()
     * @see io.helidon.validation.ValidationContext#throwOnFailure()
     */
    void check(ValidationContext context, T instance, String propertyName);

    /**
     * Validation a single property value.
     *
     * @param context      validation context
     * @param propertyName name of the property to validate
     * @param value        value of the property to validate
     * @see io.helidon.validation.ValidationContext#response()
     * @see io.helidon.validation.ValidationContext#throwOnFailure()
     */
    void checkProperty(ValidationContext context, String propertyName, Object value);
}
