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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.json.binding.JsonBinding;

public enum BindingMethod {
    STRING {
        @Override
        String serialize(JsonBinding jsonBinding, Object instance) {
            return jsonBinding.serialize(instance);
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, Class<T> type) {
            return jsonBinding.deserialize(json, type);
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, Class<? super T> type) {
            return jsonBinding.serialize(instance, type);
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, GenericType<? super T> type) {
            return jsonBinding.serialize(instance, type);
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, GenericType<T> type) {
            return jsonBinding.deserialize(json, type);
        }
    },
    READER_WRITER {
        @Override
        String serialize(JsonBinding jsonBinding, Object instance) {
            StringWriter writer = new StringWriter();
            jsonBinding.serialize(writer, instance);
            return writer.toString();
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, Class<T> type) {
            return jsonBinding.deserialize(new StringReader(json), type);
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, Class<? super T> type) {
            StringWriter writer = new StringWriter();
            jsonBinding.serialize(writer, instance, type);
            return writer.toString();
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, GenericType<? super T> type) {
            StringWriter writer = new StringWriter();
            jsonBinding.serialize(writer, instance, type);
            return writer.toString();
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, GenericType<T> type) {
            return jsonBinding.deserialize(new StringReader(json), type);
        }
    },
    STREAM {
        @Override
        String serialize(JsonBinding jsonBinding, Object instance) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jsonBinding.serialize(outputStream, instance);
            return outputStream.toString(StandardCharsets.UTF_8);
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, Class<T> type) {
            return jsonBinding.deserialize(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), type);
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, Class<? super T> type) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jsonBinding.serialize(outputStream, instance, type);
            return outputStream.toString(StandardCharsets.UTF_8);
        }

        @Override
        <T> String serialize(JsonBinding jsonBinding, T instance, GenericType<? super T> type) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            jsonBinding.serialize(outputStream, instance, type);
            return outputStream.toString(StandardCharsets.UTF_8);
        }

        @Override
        <T> T deserialize(JsonBinding jsonBinding, String json, GenericType<T> type) {
            return jsonBinding.deserialize(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), type);
        }
    };

    abstract String serialize(JsonBinding jsonBinding, Object instance);

    abstract <T> T deserialize(JsonBinding jsonBinding, String json, Class<T> type);

    abstract <T> String serialize(JsonBinding jsonBinding, T instance, Class<? super T> type);

    abstract <T> String serialize(JsonBinding jsonBinding, T instance, GenericType<? super T> type);

    abstract <T> T deserialize(JsonBinding jsonBinding, String json, GenericType<T> type);
}
