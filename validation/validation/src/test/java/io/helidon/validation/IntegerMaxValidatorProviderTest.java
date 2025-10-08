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
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
public class IntegerMaxValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    IntegerMaxValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.Integer.Max.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(IntegerMaxValidatorProviderTest.class, this);
    }

    @Test
    public void testValidIntegers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build());

        // Valid cases - integers <= max
        assertThat(validator.check(ctx, 100).failed(), is(false));
        assertThat(validator.check(ctx, 50).failed(), is(false));
        assertThat(validator.check(ctx, 0).failed(), is(false));
        assertThat(validator.check(ctx, -10).failed(), is(false));
        assertThat(validator.check(ctx, Integer.MIN_VALUE).failed(), is(false));
    }

    @Test
    public void testInvalidIntegers() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build());

        // Invalid cases - integers > max
        assertThat(validator.check(ctx, 101).failed(), is(true));
        assertThat(validator.check(ctx, 150).failed(), is(true));
        assertThat(validator.check(ctx, 1000).failed(), is(true));
        assertThat(validator.check(ctx, Integer.MAX_VALUE).failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .putValue("message", "Integer must be at most 100")
                .build());

        var response = validator.check(ctx, 150);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Integer must be at most 100"));
    }

    @Test
    public void testNonIntegerType() {
        assertThrows(ValidationException.class, () -> validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build()));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testByteType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BYTE, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 17)
                .build());

        assertThat(validator.check(ctx, (byte) 10).failed(), is(false));
        assertThat(validator.check(ctx, (byte) 7).failed(), is(false));

        var response = validator.check(ctx, (byte) 19);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("0x13 is more than 0x11"));
    }

    @Test
    public void testShortType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_SHORT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build());
        assertThat(validator.check(ctx, (short) 100).failed(), is(false));
        assertThat(validator.check(ctx, (short) 50).failed(), is(false));
        assertThat(validator.check(ctx, (short) 150).failed(), is(true));
    }

    @Test
    public void testLongType() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_LONG, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 100)
                .build());

        assertThat(validator.check(ctx, 100L).failed(), is(false));
        assertThat(validator.check(ctx, 50L).failed(), is(false));
        assertThat(validator.check(ctx, 150L).failed(), is(true));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_INT, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 0)
                .build());

        // Edge cases
        assertThat(validator.check(ctx, 0).failed(), is(false)); // Exactly equal
        assertThat(validator.check(ctx, -1).failed(), is(false)); // Just below
        assertThat(validator.check(ctx, 1).failed(), is(true)); // Just above
    }

    @Test
    public void testCharacterValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_CHAR, Annotation.builder()
                .typeName(TypeName.create(Check.Integer.Max.class))
                .putValue("value", 90) // ASCII 'Z'
                .build());

        // Character values should be treated as their ASCII values
        assertThat(validator.check(ctx, 'Z').failed(), is(false)); // ASCII 90
        assertThat(validator.check(ctx, 'A').failed(), is(false)); // ASCII 65

        var response = validator.check(ctx, '[');  // ASCII 91
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("'[' (91) is more than 'Z' (90)"));
    }
}