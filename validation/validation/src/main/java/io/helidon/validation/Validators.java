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

import io.helidon.service.registry.Services;

/**
 * Programmatic API to validate values and types.
 * There are always two methods for validation - one starting with {@code validate} and one starting with {@code check}.
 * The validate method will return a {@link io.helidon.validation.ValidationResponse}, while the check method will throw an
 * ValidationException if the validation fails.
 */
public class Validators {
    private Validators() {
    }

    /**
     * Check that the value is not null.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNotNull(Object value) {
        ValidationContext ctx = ValidationContext.create(value.getClass(), value);
        Services.get(ValidatorsService.class).validateNotNull(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the value is not null.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNotNull(Object value) {
        ValidationContext ctx = ValidationContext.create(value.getClass(), value);
        Services.get(ValidatorsService.class).validateNotNull(ctx, value);
        ctx.throwOnFailure();
    }

    /*
    TODO - add webserver-validation to add error mapper to return 400
     */
}
