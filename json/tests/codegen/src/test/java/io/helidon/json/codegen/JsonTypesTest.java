/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.common.Builder;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.types.TypeName;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBindingConfigurator;
import io.helidon.json.binding.JsonBindingFactory;
import io.helidon.json.binding.JsonConverter;
import io.helidon.json.binding.JsonDeserializer;
import io.helidon.json.binding.JsonSerializer;
import io.helidon.json.binding.Serializers;
import io.helidon.service.registry.Service;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class JsonTypesTest {

    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = JsonTypes.class.getDeclaredFields();

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

        checkField(toCheck, checked, fields, "JSON_ENTITY", Json.Entity.class);
        checkField(toCheck, checked, fields, "JSON_DESERIALIZER", Json.Deserializer.class);
        checkField(toCheck, checked, fields, "JSON_SERIALIZER", Json.Serializer.class);
        checkField(toCheck, checked, fields, "JSON_CONVERTER", Json.Converter.class);
        checkField(toCheck, checked, fields, "JSON_PROPERTY", Json.Property.class);
        checkField(toCheck, checked, fields, "JSON_IGNORE", Json.Ignore.class);
        checkField(toCheck, checked, fields, "JSON_REQUIRED", Json.Required.class);
        checkField(toCheck, checked, fields, "JSON_CREATOR", Json.Creator.class);
        checkField(toCheck, checked, fields, "JSON_SERIALIZE_NULLS", Json.SerializeNulls.class);
        checkField(toCheck, checked, fields, "JSON_PROPERTY_ORDER", Json.PropertyOrder.class);
        checkField(toCheck, checked, fields, "JSON_BUILDER_INFO", Json.BuilderInfo.class);
        checkField(toCheck, checked, fields, "JSON_FAIL_ON_UNKNOWN", Json.FailOnUnknown.class);

        checkField(toCheck, checked, fields, "JSON_DESERIALIZER_TYPE", JsonDeserializer.class);
        checkField(toCheck, checked, fields, "JSON_SERIALIZER_TYPE", JsonSerializer.class);
        checkField(toCheck, checked, fields, "JSON_CONVERTER_TYPE", JsonConverter.class);
        checkField(toCheck, checked, fields, "JSON_BINDING_CONFIGURATOR", JsonBindingConfigurator.class);
        checkField(toCheck, checked, fields, "JSON_BINDING_FACTORY", JsonBindingFactory.class);
        checkField(toCheck, checked, fields, "JSON_SERIALIZERS", Serializers.class);
        checkField(toCheck, checked, fields, "JSON_GENERATOR", JsonGenerator.class);
        checkField(toCheck, checked, fields, "JSON_PARSER", JsonParser.class);

        checkField(toCheck, checked, fields, "BUILDER_TYPE", Builder.class);
        checkField(toCheck, checked, fields, "BYTES", Bytes.class);

        checkField(toCheck, checked, fields, "SERVICE_REGISTRY_PER_LOOKUP", Service.PerLookup.class);

        // Ensure all fields have been checked
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
                fail("Field " + name + " is checked more than once.class");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
