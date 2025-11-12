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
public class NumberNegativeValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NumberNegativeValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Number.Negative.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Valid cases - negative numbers
        assertThat(validator.check(ctx, -1.0).valid(), is(true));
        assertThat(validator.check(ctx, -100.5).valid(), is(true));
        assertThat(validator.check(ctx, -0.1).valid(), is(true));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, Long.MIN_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("-123.45")).valid(), is(true));
        assertThat(validator.check(ctx, new BigInteger("-123")).valid(), is(true));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Invalid cases - non-negative numbers
        assertThat(validator.check(ctx, 0.0).valid(), is(false));
        assertThat(validator.check(ctx, 1.0).valid(), is(false));
        assertThat(validator.check(ctx, 100.5).valid(), is(false));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, Long.MAX_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("123.45")).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("0")).valid(), is(false));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.Number.Negative.class));

        // Valid string numbers
        assertThat(validator.check(ctx, "-123.45").valid(), is(true));
        assertThat(validator.check(ctx, "-0.1").valid(), is(true));
        assertThat(validator.check(ctx, "-999999").valid(), is(true));

        // Invalid string numbers
        assertThat(validator.check(ctx, "0").valid(), is(false));
        assertThat(validator.check(ctx, "123.45").valid(), is(false));
        assertThat(validator.check(ctx, "invalid").valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Negative.class))
                .putValue("message", "Number must be negative")
                .build());

        var response = validator.check(ctx, 1.0);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Number must be negative"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Test different number types
        assertThat(validator.check(ctx, (byte) -1).valid(), is(false)); // we consider byte to be unsigned
        assertThat(validator.check(ctx, (byte) 0).valid(), is(false));
        assertThat(validator.check(ctx, (byte) 1).valid(), is(false));
        assertThat(validator.check(ctx, (short) -1).valid(), is(true));
        assertThat(validator.check(ctx, (short) 0).valid(), is(false));
        assertThat(validator.check(ctx, (short) 1).valid(), is(false));
        assertThat(validator.check(ctx, -1).valid(), is(true));
        assertThat(validator.check(ctx, 0).valid(), is(false));
        assertThat(validator.check(ctx, 1).valid(), is(false));
        assertThat(validator.check(ctx, -1L).valid(), is(true));
        assertThat(validator.check(ctx, 0L).valid(), is(false));
        assertThat(validator.check(ctx, 1L).valid(), is(false));
        assertThat(validator.check(ctx, -1.0f).valid(), is(true));
        assertThat(validator.check(ctx, 0.0f).valid(), is(false));
        assertThat(validator.check(ctx, 1.0f).valid(), is(false));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.create(Validation.Number.Negative.class));

        // Edge cases
        assertThat(validator.check(ctx, -Double.MIN_VALUE).valid(), is(true)); // Smallest negative double
        assertThat(validator.check(ctx, -Double.MAX_VALUE).valid(), is(true)); // Largest negative double
        assertThat(validator.check(ctx, 0.0).valid(), is(false)); // Zero
        assertThat(validator.check(ctx, -0.0).valid(), is(false)); // Negative zero (treated as zero)
    }
}