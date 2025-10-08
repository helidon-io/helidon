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
public class StringPatternValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    StringPatternValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.String.Pattern.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(StringPatternValidatorProviderTest.class, this);
    }

    @Test
    public void testBasicPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^[a-zA-Z0-9]+$"));

        // Valid cases
        assertThat(validator.check(ctx, "hello123").failed(), is(false));
        assertThat(validator.check(ctx, "ABC").failed(), is(false));
        assertThat(validator.check(ctx, "123").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "hello world").failed(), is(true));
        assertThat(validator.check(ctx, "hello-world").failed(), is(true));
        assertThat(validator.check(ctx, "hello@world").failed(), is(true));
    }

    @Test
    public void testEmailPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\"
                                                                                             + ".[a-zA-Z]{2,}$"));

        // Valid cases
        assertThat(validator.check(ctx, "user@example.com").failed(), is(false));
        assertThat(validator.check(ctx, "test.email@domain.org").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "invalid-email").failed(), is(true));
        assertThat(validator.check(ctx, "@example.com").failed(), is(true));
        assertThat(validator.check(ctx, "user@").failed(), is(true));
    }

    @Test
    public void testPhonePattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^\\+?[1-9]\\d{1,14}$"));

        // Valid cases
        assertThat(validator.check(ctx, "+1234567890").failed(), is(false));
        assertThat(validator.check(ctx, "1234567890").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "abc123").failed(), is(true));
        assertThat(validator.check(ctx, "0123456789").failed(), is(true)); // Starts with 0
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Pattern.class))
                .putValue("value", "^[a-zA-Z]+$")
                .putValue("message", "Only letters allowed")
                .build());

        var response = validator.check(ctx, "hello123");

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Only letters allowed"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).failed(), is(false));
        assertThat(validator.check(ctx, new StringBuilder("hello123")).failed(), is(true));
    }

    @Test
    public void testComplexPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Check.String.Pattern.class,
                                                                                     "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)"
                                                                                             + "[a-zA-Z\\d@$!?&]{8,}$"));

        // Valid cases (password with at least 8 chars, 1 lowercase, 1 uppercase, 1 digit)
        assertThat(validator.check(ctx, "Password123").failed(), is(false));
        assertThat(validator.check(ctx, "MyPass123").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "password").failed(), is(true)); // No uppercase, no digit
        assertThat(validator.check(ctx, "PASSWORD").failed(), is(true)); // No lowercase, no digit
        assertThat(validator.check(ctx, "Password").failed(), is(true)); // No digit
        assertThat(validator.check(ctx, "Pass1").failed(), is(true)); // Too short
    }
}