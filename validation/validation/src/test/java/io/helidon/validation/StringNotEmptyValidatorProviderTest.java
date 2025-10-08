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
public class StringNotEmptyValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    StringNotEmptyValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.String.NotEmpty.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(StringNotEmptyValidatorProviderTest.class, this);
    }

    @Test
    public void testValidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // Valid cases - non-empty strings
        assertThat(validator.check(ctx, "hello").failed(), is(false));
        assertThat(validator.check(ctx, "a").failed(), is(false));
        assertThat(validator.check(ctx, "123").failed(), is(false));
        assertThat(validator.check(ctx, " ").failed(), is(false)); // Space is not empty
        assertThat(validator.check(ctx, "\t").failed(), is(false)); // Tab is not empty
        assertThat(validator.check(ctx, "\n").failed(), is(false)); // Newline is not empty
    }

    @Test
    public void testInvalidStrings() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // Invalid cases - empty strings
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.NotEmpty.class))
                .putValue("message", "String cannot be empty")
                .build());

        var response = validator.check(ctx, "");

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("String cannot be empty"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).failed(), is(false));
        assertThat(validator.check(ctx, new StringBuilder("")).failed(), is(true));
    }

    @Test
    public void testWhitespaceOnly() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.NotEmpty.class));

        // Whitespace-only strings are considered not empty
        assertThat(validator.check(ctx, "   ").failed(), is(false));
        assertThat(validator.check(ctx, "\t\t").failed(), is(false));
        assertThat(validator.check(ctx, "\n\n").failed(), is(false));
        assertThat(validator.check(ctx, " \t\n ").failed(), is(false));
    }
}