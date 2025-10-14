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
public class NumberNegativeOrZeroValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NumberNegativeOrZeroValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Number.NegativeOrZero.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Valid cases - negative numbers and zero
        assertThat(validator.check(ctx, -1.0).valid(), is(true));
        assertThat(validator.check(ctx, -100.5).valid(), is(true));
        assertThat(validator.check(ctx, -0.1).valid(), is(true));
        assertThat(validator.check(ctx, 0.0).valid(), is(true));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, Long.MIN_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("-123.45")).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("0")).valid(), is(true));
        assertThat(validator.check(ctx, new BigInteger("-123")).valid(), is(true));
        assertThat(validator.check(ctx, new BigInteger("0")).valid(), is(true));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Invalid cases - positive numbers
        assertThat(validator.check(ctx, 1.0).valid(), is(false));
        assertThat(validator.check(ctx, 100.5).valid(), is(false));
        assertThat(validator.check(ctx, 0.1).valid(), is(false));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, Long.MAX_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).valid(), is(false));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.Number.NegativeOrZero.class));

        // Valid string numbers
        assertThat(validator.check(ctx, "-123.45").valid(), is(true));
        assertThat(validator.check(ctx, "-0.1").valid(), is(true));
        assertThat(validator.check(ctx, "0").valid(), is(true));
        assertThat(validator.check(ctx, "-999999").valid(), is(true));

        // Invalid string numbers
        assertThat(validator.check(ctx, "123.45").valid(), is(false));
        assertThat(validator.check(ctx, "0.1").valid(), is(false));
        assertThat(validator.check(ctx, "invalid").valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.NegativeOrZero.class))
                .putValue("message", "Number must be negative or zero")
                .build());

        var response = validator.check(ctx, 1.0);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Number must be negative or zero"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Test different number types
        assertThat(validator.check(ctx, (byte) -1).valid(), is(false)); // we consider byte to be unsigned
        assertThat(validator.check(ctx, (byte) 0).valid(), is(true));
        assertThat(validator.check(ctx, (byte) 1).valid(), is(false));
        assertThat(validator.check(ctx, (short) -1).valid(), is(true));
        assertThat(validator.check(ctx, (short) 0).valid(), is(true));
        assertThat(validator.check(ctx, (short) 1).valid(), is(false));
        assertThat(validator.check(ctx, -1).valid(), is(true));
        assertThat(validator.check(ctx, 0).valid(), is(true));
        assertThat(validator.check(ctx, 1).valid(), is(false));
        assertThat(validator.check(ctx, -1L).valid(), is(true));
        assertThat(validator.check(ctx, 0L).valid(), is(true));
        assertThat(validator.check(ctx, 1L).valid(), is(false));
        assertThat(validator.check(ctx, -1.0f).valid(), is(true));
        assertThat(validator.check(ctx, 0.0f).valid(), is(true));
        assertThat(validator.check(ctx, 1.0f).valid(), is(false));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Validation.Number.NegativeOrZero.class));

        // Edge cases
        assertThat(validator.check(ctx, -Double.MIN_VALUE).valid(), is(true)); // Smallest negative double
        assertThat(validator.check(ctx, -Double.MAX_VALUE).valid(), is(true)); // Largest negative double
        assertThat(validator.check(ctx, 0.0).valid(), is(true)); // Zero
        // Negative zero (treated as positive as converted to BigDecimal, which only has one zero)
        assertThat(validator.check(ctx, -0.0).valid(), is(true));
        assertThat(validator.check(ctx, Double.MIN_VALUE).valid(), is(false)); // Smallest positive double
    }
}