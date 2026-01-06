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

package io.helidon.json.tests;

import io.helidon.common.GenericType;
import io.helidon.json.Generator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.JsonConverter;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CustomConverterTest {

    private static final JsonBinding HELIDON = Services.get(JsonBinding.class);

    @Test
    public void testCustomConverterOverTheBuilder() {
        JsonBinding jsonBinding = JsonBinding.builder()
                .addConverter(new StringConverter())
                .build();

        String original = "string value";
        String expected = "\"string value_custom_converter\"";
        String expectedDeserialized = "string value" + "_deserialized";
        assertThat(jsonBinding.serialize(original), is(expected));
        assertThat(jsonBinding.deserialize(expected, String.class), is(expectedDeserialized));
    }

    @Test
    public void testCustomDeserializerOverTheBuilder() {
        JsonBinding jsonBinding = JsonBinding.builder()
                .addDeserializer(new StringConverter())
                .build();

        String original = "string value";
        String expected = "\"string value\"";
        String expectedDeserialized = "string value" + "_deserialized";
        assertThat(jsonBinding.serialize(original), is(expected));
        assertThat(jsonBinding.deserialize(expected, String.class), is(expectedDeserialized));
    }

    @Test
    public void testCustomSerializerOverTheBuilder() {
        JsonBinding jsonBinding = JsonBinding.builder()
                .addSerializer(new StringConverter())
                .build();

        String original = "string value";
        String expected = "\"string value_custom_converter\"";
        String expectedDeserialized = "string value_custom_converter";
        assertThat(jsonBinding.serialize(original), is(expected));
        assertThat(jsonBinding.deserialize(expected, String.class), is(expectedDeserialized));
    }

    @Test
    public void testCustomSerializerOnTheField() {
        CustomFieldSerializer instance = new CustomFieldSerializer("without serializer", "with serializer");
        String expected = "{\"fieldWithoutSerializer\":\"without serializer\","
                + "\"fieldWithSerializer\":\"with serializer_custom_converter\"}";
        CustomFieldSerializer expectedDeserialized = new CustomFieldSerializer("without serializer",
                                                                               "with serializer_custom_converter");
        assertThat(HELIDON.serialize(instance), is(expected));
        assertThat(HELIDON.deserialize(expected, CustomFieldSerializer.class), is(expectedDeserialized));
    }

    @Test
    public void testCustomDeserializerOnTheField() {
        CustomFieldDeserializer instance = new CustomFieldDeserializer("without deserializer", "with deserializer");
        String expected = "{\"fieldWithoutDeserializer\":\"without deserializer\","
                + "\"fieldWithDeserializer\":\"with deserializer\"}";
        CustomFieldDeserializer expectedDeserialized = new CustomFieldDeserializer("without deserializer",
                                                                                   "with deserializer_deserialized");
        assertThat(HELIDON.serialize(instance), is(expected));
        assertThat(HELIDON.deserialize(expected, CustomFieldDeserializer.class), is(expectedDeserialized));
    }

    static class StringConverter implements JsonConverter<String> {
        @Override
        public String deserialize(JsonParser parser) {
            String string = parser.readString();
            int index = string.indexOf("_");
            if (index == -1) {
                index = string.length();
            }
            return string.substring(0, index) + "_deserialized";
        }

        @Override
        public GenericType<String> type() {
            return GenericType.create(String.class);
        }

        @Override
        public void serialize(Generator generator, String instance, boolean writeNulls) {
            generator.write(instance + "_custom_converter");
        }

        @Override
        public boolean isMapKeySerializer() {
            return true;
        }
    }

    @Json.Entity
    record CustomFieldSerializer(String fieldWithoutSerializer,
                                 @Json.Serializer(StringConverter.class) String fieldWithSerializer) {
    }

    @Json.Entity
    record CustomFieldDeserializer(String fieldWithoutDeserializer,
                                   @Json.Deserializer(StringConverter.class) String fieldWithDeserializer) {
    }

}
