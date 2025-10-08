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
    private final ConstraintValidatorContextImpl ctx;

    StringNotBlankValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.String.NotBlank.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(StringNotBlankValidatorProviderTest.class, this);
    }

    @Test
    public void testValidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // Valid cases - non-blank strings
        assertThat(validator.check(ctx, "hello").failed(), is(false));
        assertThat(validator.check(ctx, "a").failed(), is(false));
        assertThat(validator.check(ctx, "123").failed(), is(false));
        assertThat(validator.check(ctx, "hello world").failed(), is(false));
        assertThat(validator.check(ctx, "  hello  ").failed(), is(false)); // Has content despite leading/trailing spaces
    }

    @Test
    public void testInvalidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // Invalid cases - blank strings
        assertThat(validator.check(ctx, "").failed(), is(true));
        assertThat(validator.check(ctx, "   ").failed(), is(true));
        assertThat(validator.check(ctx, "\t").failed(), is(true));
        assertThat(validator.check(ctx, "\n").failed(), is(true));
        assertThat(validator.check(ctx, "\r").failed(), is(true));
        assertThat(validator.check(ctx, " \t\n ").failed(), is(true));
        assertThat(validator.check(ctx, "\u0009").failed(), is(true)); // Tab character
        assertThat(validator.check(ctx, "\u0020").failed(), is(true)); // Space character
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.NotBlank.class))
                .putValue("message", "String cannot be blank")
                .build());

        var response = validator.check(ctx, "   ");

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("String cannot be blank"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).failed(), is(false));
        assertThat(validator.check(ctx, new StringBuilder("   ")).failed(), is(true));
        assertThat(validator.check(ctx, new StringBuilder("")).failed(), is(true));
    }

    @Test
    public void testMixedWhitespace() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotBlank.class));

        // Various combinations of whitespace characters
        assertThat(validator.check(ctx, " \t ").failed(), is(true));
        assertThat(validator.check(ctx, "\n\r").failed(), is(true));
        assertThat(validator.check(ctx, "\u0009\u0020").failed(), is(true)); // Tab + Space
        assertThat(validator.check(ctx, "hello\tworld").failed(), is(false)); // Contains non-whitespace
    }
}