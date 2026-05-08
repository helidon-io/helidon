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
public class IntegerMultipleOfValidatorProviderTest {

    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    IntegerMultipleOfValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Integer.MultipleOf.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 10).valid(), is(true));
        assertThat(validator.check(ctx, 0).valid(), is(true));
        assertThat(validator.check(ctx, -15).valid(), is(true));
    }

    @Test
    public void testInvalid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 11).valid(), is(false));
        assertThat(validator.check(ctx, -14).valid(), is(false));
    }

    @Test
    public void testMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .property("message", "Integer must be divisible by 5")
                .build());

        var response = validator.check(ctx, 11);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Integer must be divisible by 5"));
    }

    @Test
    public void testUnsupportedType() {
        assertThrows(ValidationException.class, () -> validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build()));
    }

    @Test
    public void testNull() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testByte() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BYTE, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, (byte) 10).valid(), is(true));

        var response = validator.check(ctx, (byte) 9);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("0x09 is not a multiple of 0x5"));
    }

    @Test
    public void testShort() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_SHORT, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, (short) 10).valid(), is(true));
        assertThat(validator.check(ctx, (short) 12).valid(), is(false));
    }

    @Test
    public void testLong() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 10L).valid(), is(true));
        assertThat(validator.check(ctx, 12L).valid(), is(false));
    }

    @Test
    public void testChar() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_CHAR, Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 5)
                .build());

        assertThat(validator.check(ctx, 'A').valid(), is(true));

        var response = validator.check(ctx, 'C');
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("'C' (67) is not a multiple of 5"));
    }

    @Test
    public void testZeroFactor() {
        var annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Integer.MultipleOf.class))
                .property("value", 0)
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> validatorProvider.create(TypeNames.PRIMITIVE_INT, annotation));
    }
}
