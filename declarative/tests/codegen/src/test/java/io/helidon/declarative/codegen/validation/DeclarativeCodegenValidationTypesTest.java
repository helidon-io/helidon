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

package io.helidon.declarative.codegen.validation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.validation.ConstraintViolation;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationContext;
import io.helidon.validation.ValidationException;
import io.helidon.validation.ValidatorResponse;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;
import io.helidon.validation.spi.TypeValidator;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class DeclarativeCodegenValidationTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = ValidationTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            if (!declaredField.getType().equals(TypeName.class)) {
                // ignore other types
                continue;
            }
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "VALIDATION_CONTEXT", ValidationContext.class);
        checkField(toCheck, checked, fields, "CONSTRAINT_VIOLATION_LOCATION", ConstraintViolation.Location.class);
        checkField(toCheck, checked, fields, "VALIDATION_VALID", Validation.Valid.class);
        checkField(toCheck, checked, fields, "VALIDATION_CONSTRAINT", Validation.Constraint.class);
        checkField(toCheck, checked, fields, "VALIDATION_VALIDATED", Validation.Validated.class);
        checkField(toCheck, checked, fields, "VALIDATION_EXCEPTION", ValidationException.class);

        checkField(toCheck, checked, fields, "VALIDATOR_RESPONSE", ValidatorResponse.class);
        checkField(toCheck, checked, fields, "TYPE_VALIDATOR", TypeValidator.class);
        checkField(toCheck, checked, fields, "CONSTRAINT_VALIDATOR", ConstraintValidator.class);
        checkField(toCheck, checked, fields, "CONSTRAINT_VALIDATOR_PROVIDER", ConstraintValidatorProvider.class);

        assertThat("If the collection is not empty, please add appropriate checkField line to this test",
                   toCheck,
                   IsEmptyCollection.empty());
    }

    private void checkField(Set<String> namesToCheck,
                            Set<String> checkedNames,
                            Map<String, Field> namesToFields,
                            String name,
                            Class<?> expectedType) {
        Field field = namesToFields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            namesToCheck.remove(name);
            if (checkedNames.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.fqName(), is(expectedType.getCanonicalName()));
            } else {
                fail("Field " + name + " is checked more than once");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}