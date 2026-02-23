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

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A JSON parser interface for parsing JSON data from various sources.
 * <p>
 * The parser operates on a byte-by-byte basis, providing low-level access to
 * JSON tokens and values.
 * </p>
 */
public interface JsonParser {

    /**
     * Create a new JSON parser from a JSON string.
     * <p>
     * This method creates an in-memory parser that processes the entire JSON string
     * at once. Suitable for parsing small to medium-sized JSON content.
     * </p>
     *
     * @param json the JSON string to parse
     * @return a new JsonParser instance
     */
    static JsonParser create(String json) {
        Objects.requireNonNull(json);
        return create(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a new JSON parser from a byte array.
     * <p>
     * This method creates an in-memory parser that processes the entire JSON byte array
     * at once. Suitable for parsing small to medium-sized JSON content.
     * </p>
     *
     * @param json the JSON byte array to parse
     * @return a new JsonParser instance
     */
    static JsonParser create(byte[] json) {
        Objects.requireNonNull(json);
        if (json.length == 0) {
            throw new JsonException("Empty byte array provided");
        }
        return new ArrayJsonParser(json);
    }

    /**
     * Create a new JSON parser from a byte array.
     * <p>
     * This method creates an in-memory parser that processes the entire JSON byte array
     * at once. Suitable for parsing small to medium-sized JSON content.
     * </p>
     *
     * @param json the JSON byte array to parse
     * @param start start index of the json
     * @param length length of the json
     * @return a new JsonParser instance
     */
    static JsonParser create(byte[] json, int start, int length) {
        Objects.requireNonNull(json);
        if (start < 0 || length < 0 || start > json.length || start + length > json.length) {
            throw new JsonException("Invalid start/length: start="
                                            + start + ", length=" + length + ", array length=" + json.length);
        }
        return new ArrayJsonParser(json, start, length);
    }

    /**
     * Create a new JSON parser from an input stream with default buffer size.
     * <p>
     * This method creates a streaming parser that reads JSON content from the
     * input stream incrementally. Suitable for parsing large JSON content or
     * streaming sources.
     * </p>
     *
     * @param inputStream the input stream containing JSON data
     * @return a new JsonParser instance
     */
    static JsonParser create(InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        return new JsonStreamParser(inputStream);
    }

    /**
     * Create a new JSON parser from an input stream with specified buffer size.
     * <p>
     * This method creates a streaming parser with a custom buffer size for
     * reading JSON content from the input stream. Use this when you need to
     * control memory usage for large JSON documents.
     * </p>
     *
     * @param inputStream the input stream containing JSON data
     * @param bufferSize the buffer size in bytes for reading from the stream
     * @return a new JsonParser instance
     */
    static JsonParser create(InputStream inputStream, int bufferSize) {
        Objects.requireNonNull(inputStream);
        if (bufferSize <= 5) {
            throw new IllegalArgumentException("Buffer size must be greater than 5");
        }
        return new JsonStreamParser(inputStream, bufferSize);
    }

    /**
     * Create a new JSON parser from a pre-parsed JsonValue.
     * <p>
     * This method wraps an existing JsonValue in a parser interface,
     * allowing JsonValue objects to be used wherever a JsonParser is expected.
     * </p>
     *
     * @param value the JsonValue to wrap in a parser
     * @return a new JsonParser instance
     */
    static JsonParser create(JsonValue value) {
        return new JsonValueParser(value);
    }

    /**
     * Create a new JSON parser from a reader.
     * <p>
     * This method creates a streaming parser that reads JSON content from the
     * reader incrementally. Suitable for parsing large JSON content or
     * streaming sources.
     * </p>
     *
     * @param reader the reader containing JSON data
     * @return a new JsonParser instance
     */
    static JsonParser create(Reader reader) {
        Objects.requireNonNull(reader);
        return create(new ReaderInputStream(reader));
    }

    /**
     * Checks if there are more tokens available in the JSON stream.
     * <p>
     * This method should be called before attempting to read any tokens
     * to avoid exceptions when reaching the end of the JSON content.
     * </p>
     *
     * @return true if more tokens are available, false if end of stream is reached
     */
    boolean hasNext();

    /**
     * Reads the next JSON token without consuming it.
     * <p>
     * This method advances the parser to the next significant token (skipping
     * whitespace) but does not consume it. The token can then be read using
     * appropriate read methods.
     * </p>
     *
     * @return the byte value of the next token
     * @throws JsonException if no more tokens are available or parsing fails
     */
    byte nextToken();

    /**
     * Return the last byte that was read from the stream.
     * <p>
     * This method can be used to inspect the current parser position without
     * advancing it. Useful for debugging or conditional parsing logic.
     * </p>
     *
     * @return the last byte read, or 0 if no bytes have been read yet
     */
    byte currentByte();

    /**
     * Reads a complete JSON value from the current position.
     * <p>
     * This method parses and returns the next complete JSON value (object, array,
     * string, number, boolean, or null) from the current parser position.
     * </p>
     *
     * @return the parsed JsonValue
     * @throws JsonException if parsing fails or no value is available
     * @see JsonValue
     */
    JsonValue readJsonValue();

    /**
     * Reads a JSON object from the current position.
     * <p>
     * This method expects the next token to be an object start ('{') and
     * parses the complete object including all nested values.
     * </p>
     *
     * @return the parsed JsonObject
     * @throws JsonException if the next token is not an object or parsing fails
     * @see JsonObject
     */
    JsonObject readJsonObject();

    /**
     * Reads a JSON array from the current position.
     * <p>
     * This method expects the next token to be an array start ('[') and
     * parses the complete array including all nested values.
     * </p>
     *
     * @return the parsed JsonArray
     * @throws JsonException if the next token is not an array or parsing fails
     * @see JsonArray
     */
    JsonArray readJsonArray();

    /**
     * Reads a JSON string value from the current position.
     * <p>
     * This method expects the next token to be a string and returns
     * the parsed string value.
     * </p>
     *
     * @return the parsed JsonString
     * @throws JsonException if the next token is not a string or parsing fails
     * @see JsonString
     */
    JsonString readJsonString();

    /**
     * Reads a JSON number value from the current position.
     * <p>
     * This method expects the next token to be a number and returns
     * the parsed numeric value.
     * </p>
     *
     * @return the parsed JsonNumber
     * @throws JsonException if the next token is not a number or parsing fails
     * @see JsonNumber
     */
    JsonNumber readJsonNumber();

    /**
     * Reads a string value from the current position. The value has to start and end with the {@code "}.
     * <p>
     * This method expects the next token to be a string and returns
     * the string content as a Java String.
     * </p>
     *
     * @return the string value
     * @throws JsonException if the next token is not a string or parsing fails
     */
    String readString();

    /**
     * Reads a string value and returns its FNV-1a hash.
     * <p>
     * This method is optimized for performance when only string comparison
     * is needed, avoiding string object allocation.
     * </p>
     *
     * @return the hash of the string value
     * @throws JsonException if the next token is not a string or parsing fails
     */
    int readStringAsHash();

    /**
     * Reads a value as a character array.
     * <p>
     * Returns char array based on the type of the JSON value. String quotes are not included.
     * </p>
     *
     * @return character array
     * @throws JsonException if the json value is not recognized or parsing fails
     */
    char[] readCharArray();

    /**
     * Reads a char value from the current position. The value has to start and end with the {@code "}.
     * <p>
     * This method expects the next token to be a string and returns
     * the string content as a Java char value. It has to be one character.
     * </p>
     *
     * @return the char value
     * @throws JsonException if the next token is not a string or parsing fails
     */
    char readChar();

    /**
     * Reads a boolean value from the current position.
     * <p>
     * This method expects the next token to be a boolean (true/false) and
     * returns the corresponding Java boolean value.
     * </p>
     *
     * @return the boolean value
     * @throws JsonException if the next token is not a boolean or parsing fails
     */
    boolean readBoolean();

    /**
     * Reads a numeric value as a byte.
     * <p>
     * This method expects the next token to be a number and converts it to a byte.
     * </p>
     *
     * @return the byte value
     * @throws JsonException if parsing fails
     */
    byte readByte();

    /**
     * Reads a numeric value as a short.
     * <p>
     * This method expects the next token to be a number and converts it to a short.
     * </p>
     *
     * @return the short value
     * @throws JsonException if parsing fails
     */
    short readShort();

    /**
     * Reads a numeric value as an int.
     * <p>
     * This method expects the next token to be a number and converts it to an int.
     * </p>
     *
     * @return the int value
     * @throws JsonException if parsing fails
     */
    int readInt();

    /**
     * Reads a numeric value as a long.
     * <p>
     * This method expects the next token to be a number and converts it to a long.
     * </p>
     *
     * @return the long value
     * @throws JsonException if parsing fails
     */
    long readLong();

    /**
     * Reads a numeric value as a float.
     * <p>
     * This method expects the next token to be a number and converts it to a float.
     * </p>
     *
     * @return the float value
     * @throws JsonException if parsing fails
     */
    float readFloat();

    /**
     * Reads a numeric value as a double.
     * <p>
     * This method expects the next token to be a number and converts it to a double.
     * </p>
     *
     * @return the double value
     * @throws JsonException if parsing fails
     */
    double readDouble();

    /**
     * Checks if the current position contains a null value.
     * <p>
     * This method peeks at the next value to determine if it's null and advances
     * the parser position if it is.
     * </p>
     *
     * @return true if the next value is null, false otherwise
     * @throws JsonException if parsing fails
     */
    boolean checkNull();

    /**
     * Skips the current JSON value without parsing it.
     * <p>
     * This method advances the parser past the current value (object, array,
     * string, number, boolean, or null) without constructing Java objects.
     * Useful for skipping unwanted parts of large JSON documents.
     * </p>
     * @throws JsonException if parsing fails
     */
    void skip();

    /**
     * Create a JsonException with the given message.
     *
     * @param message the exception message
     * @return a JsonException
     */
    JsonException createException(String message);

    /**
     * Create a JsonException with the given message and found byte information.
     *
     * @param message the exception message
     * @param c the byte that caused the exception
     * @return a JsonException
     */
    default JsonException createException(String message, byte c) {
        if (message.endsWith(".")) {
            return createException(message + " Found: " + Parsers.toPrintableForm(c));
        }
        return createException(message + ". Found: " + Parsers.toPrintableForm(c));
    }

}
