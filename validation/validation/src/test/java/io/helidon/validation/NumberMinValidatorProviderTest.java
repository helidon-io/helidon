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
    private final ConstraintValidatorContextImpl ctx;

    NumberMinValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.Number.Min.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(NumberMinValidatorProviderTest.class, this);
    }

    @Test
    public void testValidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Valid cases - numbers >= min
        assertThat(validator.check(ctx, 10.0).failed(), is(false));
        assertThat(validator.check(ctx, 15.5).failed(), is(false));
        assertThat(validator.check(ctx, 100.0).failed(), is(false));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).failed(), is(false));
        assertThat(validator.check(ctx, Long.MAX_VALUE).failed(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("15.5")).failed(), is(false));
        assertThat(validator.check(ctx, new BigInteger("15")).failed(), is(false));
    }

    @Test
    public void testInvalidNumbers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Invalid cases - numbers < min
        assertThat(validator.check(ctx, 9.9).failed(), is(true));
        assertThat(validator.check(ctx, 5.0).failed(), is(true));
        assertThat(validator.check(ctx, 0.0).failed(), is(true));
        assertThat(validator.check(ctx, -10.0).failed(), is(true));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).failed(), is(true));
        assertThat(validator.check(ctx, Long.MIN_VALUE).failed(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("9.9")).failed(), is(true));
    }

    @Test
    public void testStringNumbers() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Valid string numbers
        assertThat(validator.check(ctx, "10.0").failed(), is(false));
        assertThat(validator.check(ctx, "15.5").failed(), is(false));
        assertThat(validator.check(ctx, "100").failed(), is(false));

        // Invalid string numbers
        assertThat(validator.check(ctx, "9.9").failed(), is(true));
        assertThat(validator.check(ctx, "5").failed(), is(true));
        assertThat(validator.check(ctx, "invalid").failed(), is(true));
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .putValue("message", "Number must be at least 10")
                .build());

        var response = validator.check(ctx, 5.0);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Number must be at least 10"));
    }

    @Test
    public void testNonNumberValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Non-number values should fail validation
        assertThat(validator.check(ctx, "hello").failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testDifferentNumberTypes() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 10.0)
                .build());

        // Test different number types
        assertThat(validator.check(ctx, (byte) 10).failed(), is(false));
        assertThat(validator.check(ctx, (byte) 15).failed(), is(false));
        assertThat(validator.check(ctx, (byte) 5).failed(), is(true));
        assertThat(validator.check(ctx, (short) 10).failed(), is(false));
        assertThat(validator.check(ctx, (short) 15).failed(), is(false));
        assertThat(validator.check(ctx, (short) 5).failed(), is(true));
        assertThat(validator.check(ctx, 10).failed(), is(false));
        assertThat(validator.check(ctx, 15).failed(), is(false));
        assertThat(validator.check(ctx, 5).failed(), is(true));
        assertThat(validator.check(ctx, 10L).failed(), is(false));
        assertThat(validator.check(ctx, 15L).failed(), is(false));
        assertThat(validator.check(ctx, 5L).failed(), is(true));
        assertThat(validator.check(ctx, 10.0f).failed(), is(false));
        assertThat(validator.check(ctx, 15.0f).failed(), is(false));
        assertThat(validator.check(ctx, 5.0f).failed(), is(true));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Check.Number.Min.class))
                .putValue("value", 0.0)
                .build());

        // Edge cases
        assertThat(validator.check(ctx, 0.0).failed(), is(false)); // Exactly equal
        // Exactly equal (BigDecimal does not have negative zero)
        assertThat(validator.check(ctx, -0.0).failed(), is(false));
        assertThat(validator.check(ctx, Double.MIN_VALUE).failed(), is(false)); // Smallest negative double
        assertThat(validator.check(ctx, -Double.MIN_VALUE).failed(), is(true)); // Highest positive double
    }
}