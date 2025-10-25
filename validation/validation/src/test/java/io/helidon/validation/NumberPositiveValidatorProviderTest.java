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
public class NumberPositiveValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NumberPositiveValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Number.Positive.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Positive.class));

        // Valid cases - positive numbers
        assertThat(validator.check(ctx, 1.0).valid(), is(true));
        assertThat(validator.check(ctx, 100.5).valid(), is(true));
        assertThat(validator.check(ctx, 0.1).valid(), is(true));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, Long.MAX_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).valid(), is(true));
        assertThat(validator.check(ctx, new BigInteger("123")).valid(), is(true));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Positive.class));

        // Invalid cases - non-positive numbers
        assertThat(validator.check(ctx, 0.0).valid(), is(false));
        assertThat(validator.check(ctx, -1.0).valid(), is(false));
        assertThat(validator.check(ctx, -100.5).valid(), is(false));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, Long.MIN_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("-123.45")).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("0")).valid(), is(false));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.Number.Positive.class));

        // Valid string numbers
        assertThat(validator.check(ctx, "123.45").valid(), is(true));
        assertThat(validator.check(ctx, "0.1").valid(), is(true));
        assertThat(validator.check(ctx, "999999").valid(), is(true));

        // Invalid string numbers
        assertThat(validator.check(ctx, "0").valid(), is(false));
        assertThat(validator.check(ctx, "-123.45").valid(), is(false));
        assertThat(validator.check(ctx, "invalid").valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Positive.class))
                .putValue("message", "Number must be positive")
                .build());

        var response = validator.check(ctx, -1.0);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Number must be positive"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Positive.class));

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Positive.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testByteNumberType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BYTE, Annotation.create(Validation.Number.Positive.class));

        // Test different number types
        assertThat(validator.check(ctx, (byte) 1).valid(), is(true));
        assertThat(validator.check(ctx, (byte) 0).valid(), is(false));
        assertThat(validator.check(ctx, (byte) -1).valid(), is(true)); // we consider byte to be unsigned
    }

    @Test
    public void testShortNumberType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_SHORT, Annotation.create(Validation.Number.Positive.class));

        assertThat(validator.check(ctx, (short) 1).valid(), is(true));
        assertThat(validator.check(ctx, (short) 0).valid(), is(false));
        assertThat(validator.check(ctx, (short) -1).valid(), is(false));
    }

    @Test
    public void testIntNumberType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.create(Validation.Number.Positive.class));

        assertThat(validator.check(ctx, 1).valid(), is(true));
        assertThat(validator.check(ctx, 0).valid(), is(false));
        assertThat(validator.check(ctx, -1).valid(), is(false));
    }

    @Test
    public void testLongNumberType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.create(Validation.Number.Positive.class));

        assertThat(validator.check(ctx, 1L).valid(), is(true));
        assertThat(validator.check(ctx, 0L).valid(), is(false));
        assertThat(validator.check(ctx, -1L).valid(), is(false));
    }

    @Test
    public void testFloatNumberType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_FLOAT, Annotation.create(Validation.Number.Positive.class));

        assertThat(validator.check(ctx, 1.0f).valid(), is(true));
        assertThat(validator.check(ctx, 0.0f).valid(), is(false));
        assertThat(validator.check(ctx, -1.0f).valid(), is(false));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Positive.class));

        // Edge cases
        assertThat(validator.check(ctx, Double.MIN_VALUE).valid(), is(true)); // Smallest positive double
        assertThat(validator.check(ctx, Double.MAX_VALUE).valid(), is(true)); // Largest positive double
        assertThat(validator.check(ctx, 0.0D).valid(), is(false)); // Zero
        assertThat(validator.check(ctx, -0.0D).valid(), is(false)); // Negative zero
        assertThat(validator.check(ctx, -Double.MIN_VALUE).valid(), is(false)); // Smallest negative double
    }
}