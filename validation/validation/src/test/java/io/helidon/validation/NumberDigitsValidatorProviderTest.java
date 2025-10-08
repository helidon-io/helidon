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

import java.math.BigDecimal;
import java.math.BigInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.validation.spi.ConstraintValidatorProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class NumberDigitsValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    NumberDigitsValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.Number.Digits.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(NumberDigitsValidatorProviderTest.class, this);
    }

    @Test
    public void testValidNumbersWithInteger() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .build());

        // Valid cases - numbers with integer <= 3
        assertThat(validator.check(ctx, 123.0).failed(), is(false));
        assertThat(validator.check(ctx, 12.0).failed(), is(false));
        assertThat(validator.check(ctx, 1.0).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123")).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("12.5")).failed(), is(false));
        assertThat(validator.check(ctx, new BigInteger("123")).failed(), is(false));
    }

    @Test
    public void testInvalidNumbersWithInteger() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .build());

        // Invalid cases - numbers with integer > 3
        assertThat(validator.check(ctx, 1234.0).failed(), is(true));
        assertThat(validator.check(ctx, 12345.0).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("1234")).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("1234.45")).failed(), is(true));
    }

    @Test
    public void testValidNumbersWithFraction() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("fraction", 2)
                .build());

        // Valid cases - numbers with fraction <= 2
        assertThat(validator.check(ctx, 123.45).failed(), is(false));
        assertThat(validator.check(ctx, 123.4).failed(), is(false));
        assertThat(validator.check(ctx, 123.0).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123.4")).failed(), is(false));
    }

    @Test
    public void testInvalidNumbersWithFraction() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("fraction", 2)
                .build());

        // Invalid cases - numbers with fraction > 2
        assertThat(validator.check(ctx, 123.456).failed(), is(true));
        assertThat(validator.check(ctx, 123.4567).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("123.456")).failed(), is(true));
    }

    @Test
    public void testValidNumbersWithBothIntegerAndFraction() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 5)
                .putValue("fraction", 2)
                .build());

        // Valid cases - numbers with integer <= 5 and fraction <= 2
        assertThat(validator.check(ctx, 123.45).failed(), is(false));
        assertThat(validator.check(ctx, 1234.5).failed(), is(false));
        assertThat(validator.check(ctx, 123.4).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("1234.5")).failed(), is(false));
    }

    @Test
    public void testInvalidNumbersWithBothIntegerAndFraction() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 5)
                .putValue("fraction", 2)
                .build());

        // Invalid cases - integer > 5
        assertThat(validator.check(ctx, 123456.0).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("123456")).failed(), is(true));

        // Invalid cases - fraction > 2
        assertThat(validator.check(ctx, 123.456).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("123.456")).failed(), is(true));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .putValue("fraction", 2)
                .build());

        // Valid string numbers
        assertThat(validator.check(ctx, "123.45").failed(), is(false));
        assertThat(validator.check(ctx, "12.5").failed(), is(false));
        assertThat(validator.check(ctx, "123").failed(), is(false));

        // Invalid string numbers
        assertThat(validator.check(ctx, "1234.5").failed(), is(true)); // integer > 3
        assertThat(validator.check(ctx, "123.456").failed(), is(true)); // fraction > 2
        assertThat(validator.check(ctx, "invalid").failed(), is(true));
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .putValue("message", "Invalid integer")
                .build());

        var response = validator.check(ctx, 1234.0);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Invalid integer"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .build());

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 3)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 2)
                .build());

        // Test different number types
        assertThat(validator.check(ctx, (byte) 12).failed(), is(false));
        assertThat(validator.check(ctx, (byte) 123).failed(), is(true));
        assertThat(validator.check(ctx, (short) 12).failed(), is(false));
        assertThat(validator.check(ctx, (short) 123).failed(), is(true));
        assertThat(validator.check(ctx, 12).failed(), is(false));
        assertThat(validator.check(ctx, 123).failed(), is(true));
        assertThat(validator.check(ctx, 12L).failed(), is(false));
        assertThat(validator.check(ctx, 123L).failed(), is(true));
        assertThat(validator.check(ctx, 12.0f).failed(), is(false));
        assertThat(validator.check(ctx, 123.0f).failed(), is(true));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Digits.class))
                .putValue("integer", 1)
                .putValue("fraction", 0)
                .build());

        // Edge cases
        assertThat(validator.check(ctx, 0.0).failed(), is(false)); // 0 has integer 1
        assertThat(validator.check(ctx, 9.0).failed(), is(false)); // 9 has integer 1
        assertThat(validator.check(ctx, 10.0).failed(), is(true)); // 10 has integer 2
        assertThat(validator.check(ctx, 0.1).failed(), is(true)); // 0.1 has fraction 1
    }
}