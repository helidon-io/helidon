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

package io.helidon.json;

import java.io.OutputStream;
import java.io.Writer;

/**
 * A JSON generator interface for writing JSON data.
 * <p>
 * This interface provides methods to generate JSON content in a streaming fashion,
 * allowing for efficient writing of large JSON documents without building
 * the entire content in memory first. It supports writing to output streams
 * and writers with automatic JSON formatting.
 * </p>
 * <p>
 * The generator provides fluent method chaining for building JSON structures
 * and handles proper JSON syntax including quotes, commas, and brackets.
 * </p>
 */
public interface JsonGenerator extends AutoCloseable {

    /**
     * Create a {@link JsonGenerator} instance to write to the provided {@link OutputStream}.
     *
     * @param outputStream output stream to write to
     * @return new Generator instance
     */
    static JsonGenerator create(OutputStream outputStream) {
        return new JsonGeneratorOutputStream(outputStream);
    }

    /**
     * Create a {@link JsonGenerator} instance to write to the provided {@link Writer}.
     *
     * @param writer writer to write to
     * @return new Generator instance
     */
    static JsonGenerator create(Writer writer) {
        return new JsonGeneratorWriter(writer);
    }

    /**
     * Write a key value to the output stream.
     * <p>
     * Each key will be automatically enclosed with quotes and a colon appended {@code "key":}.
     * This method is used for writing JSON object keys.
     * </p>
     *
     * @param key the key value to write
     * @return this generator for method chaining
     */
    JsonGenerator writeKey(String key);

    /**
     * Write a key-value pair with a string value.
     *
     * @param key   the key
     * @param value the string value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, String value);

    /**
     * Write a key-value pair with an int value.
     *
     * @param key   the key
     * @param value the int value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, int value);

    /**
     * Write a key-value pair with a long value.
     *
     * @param key   the key
     * @param value the long value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, long value);

    /**
     * Write a key-value pair with a float value.
     *
     * @param key   the key
     * @param value the float value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, float value);

    /**
     * Write a key-value pair with a double value.
     *
     * @param key   the key
     * @param value the double value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, double value);

    /**
     * Write a key-value pair with a boolean value.
     *
     * @param key   the key
     * @param value the boolean value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, boolean value);

    /**
     * Write a key-value pair with a char value.
     *
     * @param key   the key
     * @param value the char value
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, char value);

    /**
     * Write a key-value pair with a JsonValue.
     *
     * @param key   the key
     * @param value the JsonValue
     * @return this generator for method chaining
     */
    JsonGenerator write(String key, JsonValue value);

    /**
     * Write a string value.
     *
     * @param value the string value
     * @return this generator for method chaining
     */
    JsonGenerator write(String value);

    /**
     * Write a byte value.
     *
     * @param value the byte value
     * @return this generator for method chaining
     */
    JsonGenerator write(byte value);

    /**
     * Write a short value.
     *
     * @param value the short value
     * @return this generator for method chaining
     */
    JsonGenerator write(short value);

    /**
     * Write an int value.
     *
     * @param value the int value
     * @return this generator for method chaining
     */
    JsonGenerator write(int value);

    /**
     * Write a long value.
     *
     * @param value the long value
     * @return this generator for method chaining
     */
    JsonGenerator write(long value);

    /**
     * Write a float value.
     *
     * @param value the float value
     * @return this generator for method chaining
     */
    JsonGenerator write(float value);

    /**
     * Write a double value.
     *
     * @param value the double value
     * @return this generator for method chaining
     */
    JsonGenerator write(double value);

    /**
     * Write a boolean value.
     *
     * @param value the boolean value
     * @return this generator for method chaining
     */
    JsonGenerator write(boolean value);

    /**
     * Write a char value.
     *
     * @param value the char value
     * @return this generator for method chaining
     */
    JsonGenerator write(char value);

    /**
     * Write a JsonValue.
     *
     * @param value the JsonValue to write
     * @return this generator for method chaining
     */
    JsonGenerator write(JsonValue value);

    /**
     * Write a null value.
     *
     * @return this generator for method chaining
     */
    JsonGenerator writeNull();

    /**
     * Write the start of a JSON array.
     *
     * @return this generator for method chaining
     */
    JsonGenerator writeArrayStart();

    /**
     * Write the end of a JSON array.
     *
     * @return this generator for method chaining
     */
    JsonGenerator writeArrayEnd();

    /**
     * Write the start of a JSON object.
     *
     * @return this generator for method chaining
     */
    JsonGenerator writeObjectStart();

    /**
     * Write the end of a JSON object.
     *
     * @return this generator for method chaining
     */
    JsonGenerator writeObjectEnd();

    /**
     * This method does not close the stream it is writing to.
     * It only performs final writing operations.
     *
     * @throws Exception exception when closing the generator
     */
    @Override
    void close() throws Exception;
}
