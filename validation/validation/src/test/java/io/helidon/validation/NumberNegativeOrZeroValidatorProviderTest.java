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
    private final ConstraintValidatorContextImpl ctx;

    NumberNegativeOrZeroValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.Number.NegativeOrZero.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(NumberNegativeOrZeroValidatorProviderTest.class, this);
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Valid cases - negative numbers and zero
        assertThat(validator.check(ctx, -1.0).failed(), is(false));
        assertThat(validator.check(ctx, -100.5).failed(), is(false));
        assertThat(validator.check(ctx, -0.1).failed(), is(false));
        assertThat(validator.check(ctx, 0.0).failed(), is(false));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).failed(), is(false));
        assertThat(validator.check(ctx, Long.MIN_VALUE).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("-123.45")).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("0")).failed(), is(false));
        assertThat(validator.check(ctx, new BigInteger("-123")).failed(), is(false));
        assertThat(validator.check(ctx, new BigInteger("0")).failed(), is(false));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Invalid cases - positive numbers
        assertThat(validator.check(ctx, 1.0).failed(), is(true));
        assertThat(validator.check(ctx, 100.5).failed(), is(true));
        assertThat(validator.check(ctx, 0.1).failed(), is(true));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).failed(), is(true));
        assertThat(validator.check(ctx, Long.MAX_VALUE).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).failed(), is(true));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.Number.NegativeOrZero.class));

        // Valid string numbers
        assertThat(validator.check(ctx, "-123.45").failed(), is(false));
        assertThat(validator.check(ctx, "-0.1").failed(), is(false));
        assertThat(validator.check(ctx, "0").failed(), is(false));
        assertThat(validator.check(ctx, "-999999").failed(), is(false));

        // Invalid string numbers
        assertThat(validator.check(ctx, "123.45").failed(), is(true));
        assertThat(validator.check(ctx, "0.1").failed(), is(true));
        assertThat(validator.check(ctx, "invalid").failed(), is(true));
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.NegativeOrZero.class))
                .putValue("message", "Number must be negative or zero")
                .build());

        var response = validator.check(ctx, 1.0);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Number must be negative or zero"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Test different number types
        assertThat(validator.check(ctx, (byte) -1).failed(), is(true)); // we consider byte to be unsigned
        assertThat(validator.check(ctx, (byte) 0).failed(), is(false));
        assertThat(validator.check(ctx, (byte) 1).failed(), is(true));
        assertThat(validator.check(ctx, (short) -1).failed(), is(false));
        assertThat(validator.check(ctx, (short) 0).failed(), is(false));
        assertThat(validator.check(ctx, (short) 1).failed(), is(true));
        assertThat(validator.check(ctx, -1).failed(), is(false));
        assertThat(validator.check(ctx, 0).failed(), is(false));
        assertThat(validator.check(ctx, 1).failed(), is(true));
        assertThat(validator.check(ctx, -1L).failed(), is(false));
        assertThat(validator.check(ctx, 0L).failed(), is(false));
        assertThat(validator.check(ctx, 1L).failed(), is(true));
        assertThat(validator.check(ctx, -1.0f).failed(), is(false));
        assertThat(validator.check(ctx, 0.0f).failed(), is(false));
        assertThat(validator.check(ctx, 1.0f).failed(), is(true));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE,
                                                 Annotation.create(Check.Number.NegativeOrZero.class));

        // Edge cases
        assertThat(validator.check(ctx, -Double.MIN_VALUE).failed(), is(false)); // Smallest negative double
        assertThat(validator.check(ctx, -Double.MAX_VALUE).failed(), is(false)); // Largest negative double
        assertThat(validator.check(ctx, 0.0).failed(), is(false)); // Zero
        // Negative zero (treated as positive as converted to BigDecimal, which only has one zero)
        assertThat(validator.check(ctx, -0.0).failed(), is(false));
        assertThat(validator.check(ctx, Double.MIN_VALUE).failed(), is(true)); // Smallest positive double
    }
}