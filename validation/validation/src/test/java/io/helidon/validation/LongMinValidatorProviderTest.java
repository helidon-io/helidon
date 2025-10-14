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
public class LongMinValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    LongMinValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.Long.Min.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidLongs() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 10L)
                .build());

        // Valid cases - longs >= min
        assertThat(validator.check(ctx, 10L).valid(), is(true));
        assertThat(validator.check(ctx, 15L).valid(), is(true));
        assertThat(validator.check(ctx, 100L).valid(), is(true));
        assertThat(validator.check(ctx, Long.MAX_VALUE).valid(), is(true));
    }

    @Test
    public void testInvalidLongs() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 10L)
                .build());

        // Invalid cases - longs < min
        assertThat(validator.check(ctx, 9L).valid(), is(false));
        assertThat(validator.check(ctx, 5L).valid(), is(false));
        assertThat(validator.check(ctx, 0L).valid(), is(false));
        assertThat(validator.check(ctx, -10L).valid(), is(false));
        assertThat(validator.check(ctx, Long.MIN_VALUE).valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 10L)
                .putValue("message", "Long must be at least 10")
                .build());

        var response = validator.check(ctx, 5L);

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Long must be at least 10"));
    }

    @Test
    public void testNonLongValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 10L)
                .build());

        // Non-long values should fail validation
        assertThat(validator.check(ctx, "hello").valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
        assertThat(validator.check(ctx, 10.5).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 10L)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 0L)
                .build());

        // Edge cases
        assertThat(validator.check(ctx, 0L).valid(), is(true)); // Exactly equal
        assertThat(validator.check(ctx, 1L).valid(), is(true)); // Just above
        assertThat(validator.check(ctx, -1L).valid(), is(false)); // Just below
    }

    @Test
    public void testLargeValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Validation.Long.Min.class))
                .putValue("value", 1000000000L)
                .build());

        // Test with large values
        assertThat(validator.check(ctx, 1000000000L).valid(), is(true));
        assertThat(validator.check(ctx, 2000000000L).valid(), is(true));
        assertThat(validator.check(ctx, 999999999L).valid(), is(false));
    }
}