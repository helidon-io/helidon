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
public class StringEmailValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    StringEmailValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Validation.String.Email.class.getName().replace('$', '.'));
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testValidEmails() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // Valid email addresses
        assertThat(validator.check(ctx, "user@example.com").valid(), is(true));
        assertThat(validator.check(ctx, "test.email@domain.org").valid(), is(true));
        assertThat(validator.check(ctx, "user+tag@example.co.uk").valid(), is(true));
        assertThat(validator.check(ctx, "firstname.lastname@company.com").valid(), is(true));
        assertThat(validator.check(ctx, "user123@test-domain.com").valid(), is(true));
        assertThat(validator.check(ctx, "a@b.c").valid(), is(true));
        assertThat(validator.check(ctx, "user@subdomain.example.com").valid(), is(true));
    }

    @Test
    public void testInvalidEmails() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // Invalid email addresses
        assertThat(validator.check(ctx, "invalid-email").valid(), is(false));
        assertThat(validator.check(ctx, "@example.com").valid(), is(false));
        assertThat(validator.check(ctx, "user@").valid(), is(false));
        assertThat(validator.check(ctx, "user@.com").valid(), is(false));
        assertThat(validator.check(ctx, "user@example.").valid(), is(false));
        assertThat(validator.check(ctx, "user@example.com.").valid(), is(false));
        assertThat(validator.check(ctx, "user name@example.com").valid(), is(false));
        assertThat(validator.check(ctx, "user@example com").valid(), is(false));
        // Empty string causes exception in validator, skip this test
        assertThat(validator.check(ctx, "").valid(), is(false));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Validation.String.Email.class))
                .putValue("message", "Invalid email format")
                .build());

        var response = validator.check(ctx, "invalid-email");

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Invalid email format"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).valid(), is(true));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("user@example.com")).valid(), is(true));
        assertThat(validator.check(ctx, new StringBuilder("invalid-email")).valid(), is(false));
    }

    @Test
    public void testEdgeCases() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.create(Validation.String.Email.class));

        // Edge cases
        assertThat(validator.check(ctx, "a@b").valid(), is(false)); // No TLD
        assertThat(validator.check(ctx, "user@domain-with-dash.com").valid(), is(true));
        // Underscore in domain - not allowed by RFC 1035
        assertThat(validator.check(ctx, "user@domain_with_underscore.com").valid(), is(false));
    }
}