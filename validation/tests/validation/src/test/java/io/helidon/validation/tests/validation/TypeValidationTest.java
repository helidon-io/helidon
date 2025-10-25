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

package io.helidon.validation.tests.validation;

import io.helidon.testing.junit5.Testing;
import io.helidon.validation.TypeValidation;
import io.helidon.validation.ValidationResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class TypeValidationTest {
    private final TypeValidation validator;

    public TypeValidationTest(TypeValidation validator) {
        this.validator = validator;
    }

    @Test
    public void validateType() {
        ValidatedType validatedType = new ValidatedType("test_value", 42);

        ValidationResponse response = validator.validate(ValidatedType.class, validatedType);
        assertThat(response.valid(), is(true));

        validatedType = new ValidatedType("test_value", 41);
        response = validator.validate(ValidatedType.class, validatedType);
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("41 is less than 42"));
    }

    @Test
    public void validateTypeProperty() {
        var response = validator.validate(ValidatedType.class, new ValidatedType("test_value", 43), "first");
        assertThat(response.valid(), is(true));

        response = validator.validate(ValidatedType.class, new ValidatedType("bad_value", 43), "first");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("does not match pattern \".*test.*\" with flags 0"));
    }

    @Test
    public void validateTypePropertyValue() {
        var response = validator.validateProperty(ValidatedType.class, "first", "test_value");
        assertThat(response.valid(), is(true));

        response = validator.validateProperty(ValidatedType.class, "first", "bad_value");
        assertThat(response.valid(), is(false));
        assertThat(response.message(), is("does not match pattern \".*test.*\" with flags 0"));
    }
}
