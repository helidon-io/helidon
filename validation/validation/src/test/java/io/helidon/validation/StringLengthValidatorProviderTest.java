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
public class StringLengthValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    StringLengthValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.String.Length.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(StringLengthValidatorProviderTest.class, this);
    }

    @Test
    public void testMinLengthOnly() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 5)
                .build());

        // Valid cases
        assertThat(validator.check(ctx, "hello").failed(), is(false));
        assertThat(validator.check(ctx, "hello world").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "hi").failed(), is(true));
        assertThat(validator.check(ctx, "test").failed(), is(true));
        assertThat(validator.check(ctx, "").failed(), is(true));
    }

    @Test
    public void testMaxLengthOnly() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("value", 5)
                .build());

        // Valid cases
        assertThat(validator.check(ctx, "hello").failed(), is(false));
        assertThat(validator.check(ctx, "hi").failed(), is(false));
        assertThat(validator.check(ctx, "").failed(), is(false));

        // Invalid cases
        assertThat(validator.check(ctx, "hello world").failed(), is(true));
        assertThat(validator.check(ctx, "testing").failed(), is(true));
    }

    @Test
    public void testMinAndMaxLength() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 3)
                .putValue("value", 7)
                .build());

        // Valid cases
        assertThat(validator.check(ctx, "hello").failed(), is(false));
        assertThat(validator.check(ctx, "test").failed(), is(false));
        assertThat(validator.check(ctx, "testing").failed(), is(false));

        // Invalid cases - too short
        assertThat(validator.check(ctx, "hi").failed(), is(true));
        assertThat(validator.check(ctx, "").failed(), is(true));

        // Invalid cases - too long
        assertThat(validator.check(ctx, "hello world").failed(), is(true));
        assertThat(validator.check(ctx, "testing123").failed(), is(true));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 5)
                .putValue("message", "String too short")
                .build());

        var response = validator.check(ctx, "hi");

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("String too short"));
    }

    @Test
    public void testNonStringValues() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 5)
                .build());

        // Non-string values should fail validation
        assertThat(validator.check(ctx, 123).failed(), is(true));
        assertThat(validator.check(ctx, true).failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 5)
                .build());

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testStringBuilder() {
        var validator = validatorProvider.create(TypeNames.STRING, Annotation.builder()
                .typeName(TypeName.create(Check.String.Length.class))
                .putValue("min", 5)
                .build());

        // StringBuilder should work as it implements CharSequence
        assertThat(validator.check(ctx, new StringBuilder("hello")).failed(), is(false));
        assertThat(validator.check(ctx, new StringBuilder("hi")).failed(), is(true));
    }
}