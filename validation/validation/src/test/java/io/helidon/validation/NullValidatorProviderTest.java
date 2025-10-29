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
public class NullValidatorProviderTest {
    private final ConstraintValidatorProvider validatorProvider;
    private final ValidatorContext ctx;

    NullValidatorProviderTest() {
        this.validatorProvider = Services.getNamed(ConstraintValidatorProvider.class,
                                                   "io.helidon.validation.Validation.Null");
        this.ctx = new ValidatorContextImpl();
    }

    @Test
    public void testNullValue() {
        var validator = validatorProvider.create(TypeNames.OBJECT, Annotation.create(Validation.Null.class));

        var response = validator.check(ctx, null);

        assertThat(response.valid(), is(true));
    }

    @Test
    public void testNonNullValue() {
        var validator = validatorProvider.create(TypeNames.OBJECT, Annotation.create(Validation.Null.class));

        var response = validator.check(ctx, "is not null");

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("is not null"));
    }

    @Test
    public void testCustomMessage() {
        var validator = validatorProvider.create(TypeNames.OBJECT, Annotation.builder()
                .typeName(TypeName.create(Validation.Null.class))
                .putValue("message", "Value must be null")
                .build());

        var response = validator.check(ctx, "not null");

        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("Value must be null"));
    }

    @Test
    public void testDifferentTypes() {
        var validator = validatorProvider.create(TypeNames.OBJECT, Annotation.create(Validation.Null.class));

        // Test with different non-null types
        assertThat(validator.check(ctx, 42).valid(), is(false));
        assertThat(validator.check(ctx, true).valid(), is(false));
        assertThat(validator.check(ctx, new Object()).valid(), is(false));
        assertThat(validator.check(ctx, "").valid(), is(false));
    }
}