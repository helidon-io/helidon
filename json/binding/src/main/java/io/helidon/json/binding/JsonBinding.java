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

package io.helidon.json.binding;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.GenericType;
import io.helidon.json.JsonValue;

/**
 * Main interface for JSON binding operations.
 * <p>
 * This interface provides methods for serializing Java objects to JSON
 * and deserializing JSON back to Java objects. It supports various input/output
 * formats including strings, streams, readers, writers, and byte arrays.
 * </p>
 * Serialization methods must accept null as the value to serialize.
 */
public interface JsonBinding extends RuntimeType.Api<JsonBindingConfig> {

    /**
     * Create a default JsonBinding instance.
     *
     * @return a new JsonBinding instance with default configuration
     */
    static JsonBinding create() {
        return builder().build();
    }

    /**
     * Return a builder for configuring JsonBinding instances.
     *
     * @return a JsonBindingConfig.Builder
     */
    static JsonBindingConfig.Builder builder() {
        return JsonBindingConfig.builder();
    }

    /**
     * Create a JsonBinding instance with the specified configuration.
     *
     * @param config the configuration to use
     * @return a new JsonBinding instance
     */
    static JsonBinding create(JsonBindingConfig config) {
        JsonBindingImpl jsonBinding = new JsonBindingImpl(config);
        for (JsonSerializer<?> serializer : config.serializers()) {
            serializer.configure(jsonBinding);
        }
        for (JsonDeserializer<?> deserializer : config.deserializers()) {
            if ((deserializer instanceof JsonSerializer<?> serializer) && config.serializers().contains(serializer)) {
                continue;
            }
            deserializer.configure(jsonBinding);
        }
        return jsonBinding;
    }

    /**
     * Create a JsonBinding instance using the provided consumer to configure it.
     *
     * @param consumer the consumer to configure the builder
     * @return a new JsonBinding instance
     */
    static JsonBinding create(Consumer<JsonBindingConfig.Builder> consumer) {
        JsonBindingConfig.Builder builder = builder().update(consumer);
        return create(builder.buildPrototype());
    }

    /**
     * Serializes an object to a JSON string.
     * If the provided object is null, returns the string {@code null}.
     *
     * @param obj the object to serialize, this parameter may be {@code null}
     * @return the JSON string representation
     */
    String serialize(Object obj);

    /**
     * Serializes an object of a specific type to a JSON string.
     * If the provided object is null, returns the string {@code null}.
     *
     * @param obj  the object to serialize, this parameter may be {@code null}
     * @param type the class type of the object
     * @param <T>  the type of the object
     * @return     the JSON string representation
     */
    <T> String serialize(T obj, Class<? super T> type);

    /**
     * Serializes an object of a generic type to a JSON string.
     * If the provided object is null, returns the string {@code null}.
     *
     * @param obj  the object to serialize, this parameter may be {@code null}
     * @param type the generic type of the object
     * @param <T>  the type of the object
     * @return     the JSON string representation
     */
    <T> String serialize(T obj, GenericType<? super T> type);

    /**
     * Serializes an object to JSON and writes it to an OutputStream.
     * If the provided object is null, writes the bytes of the string {@code null} to the output stream.
     *
     * @param outputStream the output stream to write to
     * @param obj          the object to serialize, this parameter may be {@code null}
     */
    void serialize(OutputStream outputStream, Object obj);

    /**
     * Serializes an object of a specific type to JSON and writes it to an OutputStream.
     * If the provided object is null, writes the bytes of the string {@code null} to the output stream.
     *
     * @param outputStream the output stream to write to
     * @param obj          the object to serialize, this parameter may be {@code null}
     * @param type         the class type of the object
     * @param <T>          the type of the object
     */
    <T> void serialize(OutputStream outputStream, T obj, Class<? super T> type);

    /**
     * Serializes an object of a generic type to JSON and writes it to an OutputStream.
     * If the provided object is null, writes the bytes of the string {@code null} to the output stream.
     *
     * @param outputStream the output stream to write to
     * @param obj          the object to serialize, this parameter may be {@code null}
     * @param type         the generic type of the object
     * @param <T>          the type of the object
     */
    <T> void serialize(OutputStream outputStream, T obj, GenericType<? super T> type);

    /**
     * Serializes an object to JSON and writes it to a Writer.
     * If the provided object is null, writes the characters of {@code null} to the writer.
     *
     * @param writer the writer to write to
     * @param obj    the object to serialize, this parameter may be {@code null}
     */
    void serialize(Writer writer, Object obj);

    /**
     * Serializes an object of a specific type to JSON and writes it to a Writer.
     * If the provided object is null, writes the characters of {@code null} to the writer.
     *
     * @param writer the writer to write to
     * @param obj    the object to serialize, this parameter may be {@code null}
     * @param type   the class type of the object
     * @param <T>    the type of the object
     */
    <T> void serialize(Writer writer, T obj, Class<? super T> type);

    /**
     * Serializes an object of a generic type to JSON and writes it to a Writer.
     * If the provided object is null, writes the characters of {@code null} to the writer.
     *
     * @param writer the writer to write to
     * @param obj    the object to serialize, this parameter may be {@code null}
     * @param type   the generic type of the object
     * @param <T>    the type of the object
     */
    <T> void serialize(Writer writer, T obj, GenericType<? super T> type);

    /**
     * Deserializes JSON from a byte array to an object of the specified type.
     *
     * @param bytes the JSON data as bytes
     * @param type  the class type to deserialize to
     * @param <T>   the type of the object
     * @return      the deserialized object
     */
    <T> T deserialize(byte[] bytes, Class<T> type);

    /**
     * Deserializes JSON from a byte array to an object of the specified generic type.
     *
     * @param bytes the JSON data as bytes
     * @param type  the generic type to deserialize to
     * @param <T>   the type of the object
     * @return      the deserialized object
     */
    <T> T deserialize(byte[] bytes, GenericType<T> type);

    /**
     * Deserializes JSON from a string to an object of the specified type.
     *
     * @param jsonStr the JSON string
     * @param type    the class type to deserialize to
     * @param <T>     the type of the object
     * @return        the deserialized object
     */
    <T> T deserialize(String jsonStr, Class<T> type);

    /**
     * Deserializes JSON from a string to an object of the specified generic type.
     *
     * @param jsonStr the JSON string
     * @param type    the generic type to deserialize to
     * @param <T>     the type of the object
     * @return        the deserialized object
     */
    <T> T deserialize(String jsonStr, GenericType<T> type);

    /**
     * Deserializes JSON from an InputStream to an object of the specified type.
     *
     * @param inputStream the input stream containing JSON data
     * @param type        the class type to deserialize to
     * @param <T>         the type of the object
     * @return            the deserialized object
     */
    <T> T deserialize(InputStream inputStream, Class<T> type);

    /**
     * Deserializes JSON from an InputStream to an object of the specified generic type.
     *
     * @param inputStream the input stream containing JSON data
     * @param type        the generic type to deserialize to
     * @param <T>         the type of the object
     * @return            the deserialized object
     */
    <T> T deserialize(InputStream inputStream, GenericType<T> type);

    /**
     * Deserializes JSON from an InputStream with buffer size to an object of the specified type.
     *
     * @param inputStream the input stream containing JSON data
     * @param bufferSize  the buffer size for reading
     * @param type        the class type to deserialize to
     * @param <T>         the type of the object
     * @return            the deserialized object
     */
    <T> T deserialize(InputStream inputStream, int bufferSize, Class<T> type);

    /**
     * Deserializes JSON from an InputStream with buffer size to an object of the specified generic type.
     *
     * @param inputStream the input stream containing JSON data
     * @param bufferSize  the buffer size for reading
     * @param type        the generic type to deserialize to
     * @param <T>         the type of the object
     * @return            the deserialized object
     */
    <T> T deserialize(InputStream inputStream, int bufferSize, GenericType<T> type);

    /**
     * Deserializes JSON from a Reader to an object of the specified type.
     *
     * @param reader the reader containing JSON data
     * @param type   the class type to deserialize to
     * @param <T>    the type of the object
     * @return       the deserialized object
     */
    <T> T deserialize(Reader reader, Class<T> type);

    /**
     * Deserializes JSON from a Reader to an object of the specified generic type.
     *
     * @param reader the reader containing JSON data
     * @param type   the generic type to deserialize to
     * @param <T>    the type of the object
     * @return       the deserialized object
     */
    <T> T deserialize(Reader reader, GenericType<T> type);

    /**
     * Deserializes a JsonValue to an object of the specified type.
     *
     * @param jsonValue the JsonValue to deserialize
     * @param type      the class type to deserialize to
     * @param <T>       the type of the object
     * @return          the deserialized object
     */
    <T> T deserialize(JsonValue jsonValue, Class<T> type);

    /**
     * Deserializes a JsonValue to an object of the specified generic type.
     *
     * @param jsonValue the JsonValue to deserialize
     * @param type      the generic type to deserialize to
     * @param <T>       the type of the object
     * @return          the deserialized object
     */
    <T> T deserialize(JsonValue jsonValue, GenericType<T> type);

}
