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
        var ctx = validationContext(value);
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
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNotNull(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the value is null.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNull(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNull(ctx, value);
        return ctx.response();
    }

    private static ValidationContext validationContext(Object value) {
        if (value == null) {
            return ValidationContext.create(Validators.class);
        }
        return ValidationContext.create(value.getClass(), value);
    }

    /**
     * Check that the value is null.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNull(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNull(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the string value is a valid email address.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateEmail(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringEmail(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the string value is a valid email address.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkEmail(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringEmail(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the string value is not blank.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNotBlank(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringNotBlank(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the string value is not blank.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNotBlank(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringNotBlank(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the string value is not empty.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNotEmpty(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringNotEmpty(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the string value is not empty.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNotEmpty(CharSequence value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringNotEmpty(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the string value has the specified length.
     *
     * @param value value to check
     * @param minLength minimum length (inclusive)
     * @param maxLength maximum length (inclusive)
     * @return validation response
     */
    public static ValidationResponse validateLength(CharSequence value, int minLength, int maxLength) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringLength(ctx, value, minLength, maxLength);
        return ctx.response();
    }

    /**
     * Check that the string value has the specified length.
     *
     * @param value value to check
     * @param minLength minimum length (inclusive)
     * @param maxLength maximum length (inclusive)
     * @throws ValidationException if the validation fails
     */
    public static void checkLength(CharSequence value, int minLength, int maxLength) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringLength(ctx, value, minLength, maxLength);
        ctx.throwOnFailure();
    }

    /**
     * Check that the string value matches the specified pattern.
     *
     * @param value value to check
     * @param pattern regular expression pattern
     * @return validation response
     */
    public static ValidationResponse validatePattern(CharSequence value, String pattern) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringPattern(ctx, value, pattern);
        return ctx.response();
    }

    /**
     * Check that the string value matches the specified pattern.
     *
     * @param value value to check
     * @param pattern regular expression pattern
     * @throws ValidationException if the validation fails
     */
    public static void checkPattern(CharSequence value, String pattern) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateStringPattern(ctx, value, pattern);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is negative.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNegative(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberNegative(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the number value is negative.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNegative(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberNegative(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is negative or zero.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateNegativeOrZero(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberNegativeOrZero(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the number value is negative or zero.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkNegativeOrZero(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberNegativeOrZero(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is positive.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validatePositive(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberPositive(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the number value is positive.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkPositive(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberPositive(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is positive or zero.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validatePositiveOrZero(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberPositiveOrZero(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the number value is positive or zero.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkPositiveOrZero(Number value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberPositiveOrZero(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @return validation response
     */
    public static ValidationResponse validateMin(Number value, String minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberMin(ctx, value, minValue);
        return ctx.response();
    }

    /**
     * Check that the number value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMin(Number value, String minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberMin(ctx, value, minValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @return validation response
     */
    public static ValidationResponse validateMax(Number value, String maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberMax(ctx, value, maxValue);
        return ctx.response();
    }

    /**
     * Check that the number value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMax(Number value, String maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberMax(ctx, value, maxValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the number value has the specified number of digits.
     *
     * @param value value to check
     * @param integerDigits maximum number of integer digits
     * @param fractionDigits maximum number of fraction digits
     * @return validation response
     */
    public static ValidationResponse validateDigits(Number value, int integerDigits, int fractionDigits) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberDigits(ctx, value, integerDigits, fractionDigits);
        return ctx.response();
    }

    /**
     * Check that the number value has the specified number of digits.
     *
     * @param value value to check
     * @param integerDigits maximum number of integer digits
     * @param fractionDigits maximum number of fraction digits
     * @throws ValidationException if the validation fails
     */
    public static void checkDigits(Number value, int integerDigits, int fractionDigits) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateNumberDigits(ctx, value, integerDigits, fractionDigits);
        ctx.throwOnFailure();
    }

    /**
     * Check that the integer value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @return validation response
     */
    public static ValidationResponse validateMin(Integer value, int minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateIntegerMin(ctx, value, minValue);
        return ctx.response();
    }

    /**
     * Check that the integer value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMin(Integer value, int minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateIntegerMin(ctx, value, minValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the integer value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @return validation response
     */
    public static ValidationResponse validateMax(Integer value, int maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateIntegerMax(ctx, value, maxValue);
        return ctx.response();
    }

    /**
     * Check that the integer value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMax(Integer value, int maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateIntegerMax(ctx, value, maxValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the long value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @return validation response
     */
    public static ValidationResponse validateMin(Long value, long minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateLongMin(ctx, value, minValue);
        return ctx.response();
    }

    /**
     * Check that the long value is greater than or equal to the minimum value.
     *
     * @param value value to check
     * @param minValue minimum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMin(Long value, long minValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateLongMin(ctx, value, minValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the long value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @return validation response
     */
    public static ValidationResponse validateMax(Long value, long maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateLongMax(ctx, value, maxValue);
        return ctx.response();
    }

    /**
     * Check that the long value is less than or equal to the maximum value.
     *
     * @param value value to check
     * @param maxValue maximum value
     * @throws ValidationException if the validation fails
     */
    public static void checkMax(Long value, long maxValue) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateLongMax(ctx, value, maxValue);
        ctx.throwOnFailure();
    }

    /**
     * Check that the boolean value is true.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateTrue(Boolean value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateBooleanTrue(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the boolean value is true.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkTrue(Boolean value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateBooleanTrue(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the boolean value is false.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateFalse(Boolean value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateBooleanFalse(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the boolean value is false.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkFalse(Boolean value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateBooleanFalse(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the calendar value is in the future.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateCalendarFuture(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarFuture(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the calendar value is in the future.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkCalendarFuture(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarFuture(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the calendar value is in the future or present.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateCalendarFutureOrPresent(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarFutureOrPresent(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the calendar value is in the future or present.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkCalendarFutureOrPresent(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarFutureOrPresent(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the calendar value is in the past.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateCalendarPast(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarPast(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the calendar value is in the past.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkCalendarPast(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarPast(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the calendar value is in the past or present.
     *
     * @param value value to check
     * @return validation response
     */
    public static ValidationResponse validateCalendarPastOrPresent(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarPastOrPresent(ctx, value);
        return ctx.response();
    }

    /**
     * Check that the calendar value is in the past or present.
     *
     * @param value value to check
     * @throws ValidationException if the validation fails
     */
    public static void checkCalendarPastOrPresent(Object value) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCalendarPastOrPresent(ctx, value);
        ctx.throwOnFailure();
    }

    /**
     * Check that the collection value has the specified size.
     *
     * @param value value to check
     * @param minSize minimum size (inclusive)
     * @param maxSize maximum size (inclusive)
     * @return validation response
     */
    public static ValidationResponse validateCollectionSize(Object value, int minSize, int maxSize) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCollectionSize(ctx, value, minSize, maxSize);
        return ctx.response();
    }

    /**
     * Check that the collection value has the specified size.
     *
     * @param value value to check
     * @param minSize minimum size (inclusive)
     * @param maxSize maximum size (inclusive)
     * @throws ValidationException if the validation fails
     */
    public static void checkCollectionSize(Object value, int minSize, int maxSize) {
        var ctx = validationContext(value);
        Services.get(ValidatorsService.class).validateCollectionSize(ctx, value, minSize, maxSize);
        ctx.throwOnFailure();
    }
}
