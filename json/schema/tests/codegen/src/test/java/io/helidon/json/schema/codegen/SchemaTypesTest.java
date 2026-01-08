/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.json.schema.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.common.types.TypeName;
import io.helidon.json.schema.JsonSchema;
import io.helidon.json.schema.Schema;
import io.helidon.json.schema.spi.JsonSchemaProvider;
import io.helidon.service.registry.Service;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class SchemaTypesTest {

    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = SchemaTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            assertThat(name + " must be a TypeName", declaredField.getType(), sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be package local, not public",
                       Modifier.isPublic(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not private",
                       Modifier.isPrivate(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not protected",
                       Modifier.isProtected(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "JSON_SCHEMA_SCHEMA", JsonSchema.Schema.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_ID", JsonSchema.Id.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_TITLE", JsonSchema.Title.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_DESCRIPTION", JsonSchema.Description.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_REQUIRED", JsonSchema.Required.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_DO_NOT_INSPECT", JsonSchema.DoNotInspect.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_IGNORE", JsonSchema.Ignore.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_PROPERTY_NAME", JsonSchema.PropertyName.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_PROVIDER", JsonSchemaProvider.class);

        checkField(toCheck, checked, fields, "JSON_SCHEMA_INTEGER_MULTIPLE_OF", JsonSchema.Integer.MultipleOf.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_INTEGER_MINIMUM", JsonSchema.Integer.Minimum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_INTEGER_MAXIMUM", JsonSchema.Integer.Maximum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_INTEGER_EXCLUSIVE_MAXIMUM",
                   JsonSchema.Integer.ExclusiveMaximum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_INTEGER_EXCLUSIVE_MINIMUM",
                   JsonSchema.Integer.ExclusiveMinimum.class);

        checkField(toCheck, checked, fields, "JSON_SCHEMA_STRING_MIN_LENGTH", JsonSchema.String.MinLength.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_STRING_MAX_LENGTH", JsonSchema.String.MaxLength.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_STRING_PATTERN", JsonSchema.String.Pattern.class);

        checkField(toCheck, checked, fields, "JSON_SCHEMA_OBJECT_MIN_PROPERTIES", JsonSchema.Object.MinProperties.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_OBJECT_MAX_PROPERTIES", JsonSchema.Object.MaxProperties.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_OBJECT_ADDITIONAL_PROPERTIES",
                   JsonSchema.Object.AdditionalProperties.class);

        checkField(toCheck, checked, fields, "JSON_SCHEMA_NUMBER_MULTIPLE_OF", JsonSchema.Number.MultipleOf.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_NUMBER_MINIMUM", JsonSchema.Number.Minimum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_NUMBER_MAXIMUM", JsonSchema.Number.Maximum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_NUMBER_EXCLUSIVE_MAXIMUM",
                   JsonSchema.Number.ExclusiveMaximum.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_NUMBER_EXCLUSIVE_MINIMUM",
                   JsonSchema.Number.ExclusiveMinimum.class);

        checkField(toCheck, checked, fields, "JSON_SCHEMA_ARRAY_MAX_ITEMS", JsonSchema.Array.MaxItems.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_ARRAY_MIN_ITEMS", JsonSchema.Array.MinItems.class);
        checkField(toCheck, checked, fields, "JSON_SCHEMA_ARRAY_UNIQUE_ITEMS", JsonSchema.Array.UniqueItems.class);

        checkField(toCheck, checked, fields, "SCHEMA", Schema.class);

        checkField(toCheck, checked, fields, "LAZY_VALUE", LazyValue.class);

        checkField(toCheck, checked, fields, "JSONB_TRANSIENT", JsonbTransient.class);
        checkField(toCheck, checked, fields, "JSONB_PROPERTY", JsonbProperty.class);
        checkField(toCheck, checked, fields, "JSONB_CREATOR", JsonbCreator.class);

        checkField(toCheck, checked, fields, "SERVICE_NAMED_BY_TYPE", Service.NamedByType.class);
        checkField(toCheck, checked, fields, "SERVICE_SINGLETON", Service.Singleton.class);
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
                fail("Field " + name + " is checked more than once.class");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
