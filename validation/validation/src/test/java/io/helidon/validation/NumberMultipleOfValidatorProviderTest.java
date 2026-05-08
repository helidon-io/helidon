/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.validation.spi.ConstraintValidatorProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class NumberMultipleOfValidatorProviderTest {

    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NumberMultipleOfValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Number.MultipleOf.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 10).valid(), is(true));
        assertThat(validator.check(ctx, 0).valid(), is(true));
        assertThat(validator.check(ctx, -15).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("20")).valid(), is(true));
    }

    @Test
    public void testInvalid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 11).valid(), is(false));
        assertThat(validator.check(ctx, -14).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("4")).valid(), is(false));
    }

    @Test
    public void testValidDecimal() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .build());

        assertThat(validator.check(ctx, 1.10d).valid(), is(true));
        assertThat(validator.check(ctx, 0.15f).valid(), is(true));
        assertThat(validator.check(ctx, -0.20d).valid(), is(true));
        assertThat(validator.check(ctx, new BigDecimal("0.35")).valid(), is(true));
    }

    @Test
    public void testInvalidDecimal() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .build());

        assertThat(validator.check(ctx, 1.11d).valid(), is(false));
        assertThat(validator.check(ctx, new BigDecimal("0.071")).valid(), is(false));
    }

    @Test
    public void testString() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .build());

        assertThat(validator.check(ctx, "1.10").valid(), is(true));
        assertThat(validator.check(ctx, "-0.20").valid(), is(true));
        assertThat(validator.check(ctx, "1.11").valid(), is(false));
        assertThat(validator.check(ctx, "invalid").valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .property("message", "Number must be divisible by 0.05")
                .build());

        var response = validator.check(ctx, 1.11d);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Number must be divisible by 0.05"));
    }

    @Test
    public void testUnsupported() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .build());

        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNull() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0.05")
                .build());

        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testZeroFactor() {
        var annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Number.MultipleOf.class))
                .property("value", "0")
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> validatorProvider.create(TypeNames.PRIMITIVE_DOUBLE, annotation));
    }
}
