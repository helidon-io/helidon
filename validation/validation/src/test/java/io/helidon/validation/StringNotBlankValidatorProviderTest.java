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
public class StringNotBlankValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    StringNotBlankValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.String.NotBlank.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // Valid cases - non-blank strings
        assertThat(validator.check(ctx, "hello").valid(), is(true));
        assertThat(validator.check(ctx, "a").valid(), is(true));
        assertThat(validator.check(ctx, "123").valid(), is(true));
        assertThat(validator.check(ctx, "hello world").valid(), is(true));
        assertThat(validator.check(ctx, "  hello  ").valid(), is(true)); // Has content despite leading/trailing spaces
    }

    @Test
    public void testInvalidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // Invalid cases - blank strings
        assertThat(validator.check(ctx, "").valid(), is(false));
        assertThat(validator.check(ctx, "   ").valid(), is(false));
        assertThat(validator.check(ctx, "\t").valid(), is(false));
        assertThat(validator.check(ctx, "\n").valid(), is(false));
        assertThat(validator.check(ctx, "\r").valid(), is(false));
        assertThat(validator.check(ctx, " \t\n ").valid(), is(false));
        assertThat(validator.check(ctx, "\u0009").valid(), is(false)); // Tab character
        assertThat(validator.check(ctx, "\u0020").valid(), is(false)); // Space character
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.String.NotBlank.class))
                .putValue("message", "String cannot be blank")
                .build());

        var response = validator.check(ctx, "   ");

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("String cannot be blank"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).valid(), is(true));
        assertThat(validator.check(ctx, new StringBuilder("   ")).valid(), is(false));
        assertThat(validator.check(ctx, new StringBuilder("")).valid(), is(false));
    }

    @Test
    public void testMixedWhitespace() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.NotBlank.class));

        // Various combinations of whitespace characters
        assertThat(validator.check(ctx, " \t ").valid(), is(false));
        assertThat(validator.check(ctx, "\n\r").valid(), is(false));
        assertThat(validator.check(ctx, "\u0009\u0020").valid(), is(false)); // Tab + Space
        assertThat(validator.check(ctx, "hello\tworld").valid(), is(true)); // Contains non-whitespace
    }
}