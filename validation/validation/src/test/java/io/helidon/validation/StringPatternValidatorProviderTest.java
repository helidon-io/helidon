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
    private final ValidatorContext ctx;

    StringPatternValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.String.Pattern.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testBasicPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^[a-zA-Z0-9]+$"));

        // Valid cases
        assertThat(validator.check(ctx, "hello123").valid(), is(true));
        assertThat(validator.check(ctx, "ABC").valid(), is(true));
        assertThat(validator.check(ctx, "123").valid(), is(true));

        // Invalid cases
        assertThat(validator.check(ctx, "hello world").valid(), is(false));
        assertThat(validator.check(ctx, "hello-world").valid(), is(false));
        assertThat(validator.check(ctx, "hello@world").valid(), is(false));
    }

    @Test
    public void testEmailPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\\"
                                                                                             + ".[a-zA-Z]{2,}$"));

        // Valid cases
        assertThat(validator.check(ctx, "user@example.com").valid(), is(true));
        assertThat(validator.check(ctx, "test.email@domain.org").valid(), is(true));

        // Invalid cases
        assertThat(validator.check(ctx, "invalid-email").valid(), is(false));
        assertThat(validator.check(ctx, "@example.com").valid(), is(false));
        assertThat(validator.check(ctx, "user@").valid(), is(false));
    }

    @Test
    public void testPhonePattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^\\+?[1-9]\\d{1,14}$"));

        // Valid cases
        assertThat(validator.check(ctx, "+1234567890").valid(), is(true));
        assertThat(validator.check(ctx, "1234567890").valid(), is(true));

        // Invalid cases
        assertThat(validator.check(ctx, "abc123").valid(), is(false));
        assertThat(validator.check(ctx, "0123456789").valid(), is(false)); // Starts with 0
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.String.Pattern.class))
                .putValue("value", "^[a-zA-Z]+$")
                .putValue("message", "Only letters allowed")
                .build());

        var response = validator.check(ctx, "hello123");

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Only letters allowed"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^[a-zA-Z]+$"));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).valid(), is(true));
        assertThat(validator.check(ctx, new StringBuilder("hello123")).valid(), is(false));
    }

    @Test
    public void testComplexPattern() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class,
                                                                                     "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)"
                                                                                             + "[a-zA-Z\\d@$!?&]{8,}$"));

        // Valid cases (password with at least 8 chars, 1 lowercase, 1 uppercase, 1 digit)
        assertThat(validator.check(ctx, "Password123").valid(), is(true));
        assertThat(validator.check(ctx, "MyPass123").valid(), is(true));

        // Invalid cases
        assertThat(validator.check(ctx, "password").valid(), is(false)); // No uppercase, no digit
        assertThat(validator.check(ctx, "PASSWORD").valid(), is(false)); // No lowercase, no digit
        assertThat(validator.check(ctx, "Password").valid(), is(false)); // No digit
        assertThat(validator.check(ctx, "Pass1").valid(), is(false)); // Too short
    }
}