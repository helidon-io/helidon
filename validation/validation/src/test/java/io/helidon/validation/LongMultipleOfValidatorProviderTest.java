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
public class LongMultipleOfValidatorProviderTest {

    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    LongMultipleOfValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Long.MultipleOf.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 10L)
                .build());

        assertThat(validator.check(ctx, 10L).valid(), is(true));
        assertThat(validator.check(ctx, 0L).valid(), is(true));
        assertThat(validator.check(ctx, -20L).valid(), is(true));
    }

    @Test
    public void testInvalid() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 10L)
                .build());

        assertThat(validator.check(ctx, 11L).valid(), is(false));
        assertThat(validator.check(ctx, -21L).valid(), is(false));
    }

    @Test
    public void testMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 10L)
                .property("message", "Long must be divisible by 10")
                .build());

        var response = validator.check(ctx, 11L);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Long must be divisible by 10"));
    }

    @Test
    public void testUnsupported() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 10L)
                .build());

        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
        assertThat(validator.check(ctx, 10).valid(), is(false));
    }

    @Test
    public void testNull() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 10L)
                .build());

        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testZeroFactor() {
        var annotation = Annotation.builder()
                .typeName(TypeName.create(Validation.Long.MultipleOf.class))
                .property("value", 0L)
                .build();

        assertThrows(IllegalArgumentException.class,
                     () -> validatorProvider.create(TypeNames.PRIMITIVE_LONG, annotation));
    }
}
