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
public class BooleanFalseValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ConstraintValidatorContextImpl ctx;

    BooleanFalseValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   Check.Boolean.False.class.getName().replace('$', '.'));
        this.ctx = new ConstraintValidatorContextImpl(BooleanFalseValidatorProviderTest.class, this);
    }

    @Test
    public void testValidBooleans() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Valid cases - false values
        assertThat(validator.check(ctx, false).failed(), is(false));
        assertThat(validator.check(ctx, Boolean.FALSE).failed(), is(false));
    }

    @Test
    public void testValidBooleansBoxed() {
        var validator = validatorProvider.create(TypeNames.BOXED_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Valid cases - false values
        assertThat(validator.check(ctx, false).failed(), is(false));
        assertThat(validator.check(ctx, Boolean.FALSE).failed(), is(false));
    }

    @Test
    public void testInvalidBooleans() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Invalid cases - true values
        assertThat(validator.check(ctx, true).failed(), is(true));

        var response = validator.check(ctx, Boolean.TRUE);
        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("must be false"));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.builder()
                .typeName(TypeName.create(Check.Boolean.False.class))
                .putValue("message", "Value must be false")
                .build());

        var response = validator.check(ctx, true);

        assertThat(response.failed(), is(true));
        assertThat(response.message(), is("Value must be false"));
    }

    @Test
    public void testNonBooleanValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Non-boolean values should fail validation
        assertThat(validator.check(ctx, "false").failed(), is(true));
        assertThat(validator.check(ctx, "true").failed(), is(true));
        assertThat(validator.check(ctx, 1).failed(), is(true));
        assertThat(validator.check(ctx, 0).failed(), is(true));
        assertThat(validator.check(ctx, "hello").failed(), is(true));
        assertThat(validator.check(ctx, new Object()).failed(), is(true));
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Null values should be considered valid (not sent to validator)
        assertThat(validator.check(ctx, null).failed(), is(false));
    }

    @Test
    public void testStringRepresentations() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // String representations should not be automatically converted
        assertThat(validator.check(ctx, "false").failed(), is(true));
        assertThat(validator.check(ctx, "FALSE").failed(), is(true));
        assertThat(validator.check(ctx, "0").failed(), is(true));
        assertThat(validator.check(ctx, "no").failed(), is(true));
    }

    @Test
    public void testNumericValues() {
        var validator = validatorProvider.create(TypeNames.PRIMITIVE_BOOLEAN, Annotation.create(Check.Boolean.False.class));

        // Numeric values should not be automatically converted
        assertThat(validator.check(ctx, 1).failed(), is(true));
        assertThat(validator.check(ctx, 0).failed(), is(true));
        assertThat(validator.check(ctx, -1).failed(), is(true));
        assertThat(validator.check(ctx, 1.0).failed(), is(true));
        assertThat(validator.check(ctx, 0.0).failed(), is(true));
    }
}