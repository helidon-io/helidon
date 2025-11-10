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
public class NumberMinValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NumberMinValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Number.Min.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Valid cases - numbers >= min
        assertThat(validator.check(ctx, 10.0).valid(), is(true));
        assertThat(validator.check(ctx, 15.5).valid(), is(true));
        assertThat(validator.check(ctx, 100.0).valid(), is(true));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, Long.MAX_VALUE).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("15.5")).valid(), is(true));
        assertThat(validator.check(ctx, new BigInteger("15")).valid(), is(true));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Invalid cases - numbers < min
        assertThat(validator.check(ctx, 9.9).valid(), is(false));
        assertThat(validator.check(ctx, 5.0).valid(), is(false));
        assertThat(validator.check(ctx, 0.0).valid(), is(false));
        assertThat(validator.check(ctx, -10.0).valid(), is(false));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, Long.MIN_VALUE).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("9.9")).valid(), is(false));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Valid string numbers
        assertThat(validator.check(ctx, "10.0").valid(), is(true));
        assertThat(validator.check(ctx, "15.5").valid(), is(true));
        assertThat(validator.check(ctx, "100").valid(), is(true));

        // Invalid string numbers
        assertThat(validator.check(ctx, "9.9").valid(), is(false));
        assertThat(validator.check(ctx, "5").valid(), is(false));
        assertThat(validator.check(ctx, "invalid").valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .putValue("message", "Number must be at least 10")
                .build());

        var response = validator.check(ctx, 5.0);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Number must be at least 10"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Test different number types
        assertThat(validator.check(ctx, (byte) 10).valid(), is(true));
        assertThat(validator.check(ctx, (byte) 15).valid(), is(true));
        assertThat(validator.check(ctx, (byte) 5).valid(), is(false));
        assertThat(validator.check(ctx, (short) 10).valid(), is(true));
        assertThat(validator.check(ctx, (short) 15).valid(), is(true));
        assertThat(validator.check(ctx, (short) 5).valid(), is(false));
        assertThat(validator.check(ctx, 10).valid(), is(true));
        assertThat(validator.check(ctx, 15).valid(), is(true));
        assertThat(validator.check(ctx, 5).valid(), is(false));
        assertThat(validator.check(ctx, 10L).valid(), is(true));
        assertThat(validator.check(ctx, 15L).valid(), is(true));
        assertThat(validator.check(ctx, 5L).valid(), is(false));
        assertThat(validator.check(ctx, 10.0f).valid(), is(true));
        assertThat(validator.check(ctx, 15.0f).valid(), is(true));
        assertThat(validator.check(ctx, 5.0f).valid(), is(false));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.Min.class))
                .putValue("value", 0.0)
                .build());

        // Edge cases
        assertThat(validator.check(ctx, 0.0).valid(), is(true)); // Exactly equal
        // Exactly equal (BigDecimal does not have negative zero)
        assertThat(validator.check(ctx, -0.0).valid(), is(true));
        assertThat(validator.check(ctx, Double.MIN_VALUE).valid(), is(true)); // Smallest negative double
        assertThat(validator.check(ctx, -Double.MIN_VALUE).valid(), is(false)); // Highest positive double
    }
}