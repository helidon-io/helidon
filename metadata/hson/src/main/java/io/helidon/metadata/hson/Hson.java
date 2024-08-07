/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.hson;

import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Main entry point for Helidon metadata format parsing and writing.
 * <p>
 * This is a simplified JSON, so the same types are available (see {@link io.helidon.metadata.hson.Hson.Type}).
 */
public final class Hson {
    private Hson() {
    }

    /**
     * Parse the data into a value.
     *
     * @param inputStream stream to parse
     * @return a value
     * @see io.helidon.metadata.hson.Hson.Value#type()
     * @see io.helidon.metadata.hson.Hson.Value#asStruct()
     * @see io.helidon.metadata.hson.Hson.Value#asArray()
     */
    public static Value<?> parse(InputStream inputStream) {
        return HsonParser.parse(inputStream);
    }

    /**
     * A new fluent API builder to construct na HSON Struct.
     *
     * @return a new builder
     */
    public static Struct.Builder structBuilder() {
        return Struct.builder();
    }

    /**
     * The type of value.
     */
    public enum Type {
        /**
         * String value.
         */
        STRING,
        /**
         * Numeric value.
         */
        NUMBER,
        /**
         * Boolean value.
         */
        BOOLEAN,
        /**
         * Null value.
         */
        NULL,
        /**
         * Nested struct value.
         */
        STRUCT,
        /**
         * Array value.
         */
        ARRAY
    }

    /**
     * HSON Struct.
     * <p>
     * A representation of a struct, with possible child values. This is similar to {@code JsonObject} in JSON-P.
     */
    public sealed interface Struct extends Value<Struct> permits HsonStruct {

        /**
         * A new fluent API builder to construct a HSON Struct.
         *
         * @return a new builder
         */
        static Builder builder() {
            return new HsonStruct.Builder();
        }

        /**
         * Create an empty struct.
         *
         * @return new empty instance
         */
        static Struct create() {
            return builder().build();
        }

        /**
         * Get a value.
         *
         * @param key key under this struct
         * @return value of that key, or empty if not present; may return value that represents null
         * @see io.helidon.metadata.hson.Hson.Type
         */
        Optional<Value<?>> value(String key);

        /**
         * Get a boolean value.
         *
         * @param key key under this struct
         * @return boolean value if present
         * @throws HsonException in case the key exists, but is not a {@code boolean}
         */
        Optional<Boolean> booleanValue(String key);

        /**
         * Get a boolean value with default if not defined.
         *
         * @param key          key under this struct
         * @param defaultValue default value to use if the key does not exist
         * @return boolean value, or default value if the key does not exist
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#BOOLEAN}
         */
        boolean booleanValue(String key, boolean defaultValue);

        /**
         * Get struct value. If the value represents {@code null}, returns empty optional.
         *
         * @param key key under this struct
         * @return struct value if present
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#STRUCT}
         */
        Optional<Struct> structValue(String key);

        /**
         * Get string value.
         *
         * @param key key under this struct
         * @return string value if present
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#STRING}
         */
        Optional<String> stringValue(String key);

        /**
         * Get a string value with default if not defined.
         *
         * @param key          key under this struct
         * @param defaultValue default value to use if the key does not exist
         * @return string value, or default value if the key does not exist
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#STRING}
         */
        String stringValue(String key, String defaultValue);

        /**
         * Get int value.
         *
         * @param key key under this struct
         * @return int value if present, from {@link java.math.BigDecimal#intValue()}
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#NUMBER}
         */
        Optional<Integer> intValue(String key);

        /**
         * Get an int value with default if not defined.
         *
         * @param key          key under this struct
         * @param defaultValue default value to use if the key does not exist
         * @return int value, or default value if the key does not exist
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#NUMBER}
         * @see #intValue(String)
         */
        int intValue(String key, int defaultValue);

        /**
         * Get double value.
         *
         * @param key key under this struct
         * @return double value if present, from {@link java.math.BigDecimal#doubleValue()}
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#NUMBER}
         */
        Optional<Double> doubleValue(String key);

        /**
         * Get a double value with default if not defined (or null).
         *
         * @param key          key under this struct
         * @param defaultValue default value to use if the key does not exist
         * @return double value, or default value if the key does not exist
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#NUMBER}
         * @see #doubleValue(String)
         */
        double doubleValue(String key, double defaultValue);

        /**
         * Get number value.
         *
         * @param key key under this struct
         * @return big decimal value if present
         * @throws HsonException in case the key exists, but is not a
         *                       {@link io.helidon.metadata.hson.Hson.Type#NUMBER}
         */
        Optional<BigDecimal> numberValue(String key);

        /**
         * Get number value with default if not defined (or null).
         *
         * @param key          key under this struct
         * @param defaultValue default value to use if not present or null
         * @return big decimal value
         */
        BigDecimal numberValue(String key, BigDecimal defaultValue);

        /**
         * Get string array value.
         *
         * @param key key under this struct
         * @return string array value, if the key exists
         * @throws HsonException in case the key exists, is an array, but elements are not strings
         * @throws HsonException in case the key exists, but is not an array
         */
        Optional<List<String>> stringArray(String key);

        /**
         * Get struct array value.
         *
         * @param key key under this struct
         * @return struct array value, if the key exists
         * @throws HsonException in case the key exists, is an array, but elements are not structs
         * @throws HsonException in case the key exists, but is not an array
         */
        Optional<List<Struct>> structArray(String key);

        /**
         * Get number array value.
         *
         * @param key key under this struct
         * @return number array value, if the key exists
         * @throws HsonException in case the key exists, is an array, but elements are not numbers
         * @throws HsonException in case the key exists, but is not an array
         */
        Optional<List<BigDecimal>> numberArray(String key);

        /**
         * Get boolean array value.
         *
         * @param key key under this struct
         * @return boolean array value, if the key exists
         * @throws HsonException in case the key exists, is an array, but elements are not booleans
         * @throws HsonException in case the key exists, but is not an array
         */
        Optional<List<Boolean>> booleanArray(String key);

        /**
         * Get array value.
         *
         * @param key key under this struct
         * @return array value, if the key exists
         * @throws HsonException in case the key exists, but is not an array
         */
        Optional<Array> arrayValue(String key);

        /**
         * Fluent API builder for {@link io.helidon.metadata.hson.Hson.Struct}.
         *
         * @see #build()
         */
        interface Builder extends io.helidon.common.Builder<Builder, Struct> {
            /**
             * Unset an existing value assigned to the key.
             * This method does not care if the key is mapped or not.
             *
             * @param key key to unset
             * @return updated instance (this instance)
             */
            Builder unset(String key);

            /**
             * Set a null value for the specified key.
             *
             * @param key key to set
             * @return updated instance (this instance)
             */
            Builder setNull(String key);

            /**
             * Set a value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, Value<?> value);

            /**
             * Set a string value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, String value);

            /**
             * Set a boolean value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, boolean value);

            /**
             * Set a double value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, double value);

            /**
             * Set a float value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, float value);

            /**
             * Set an int value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, int value);

            /**
             * Set a long value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, long value);

            /**
             * Set a {@link java.math.BigDecimal} value.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, BigDecimal value);

            /**
             * Set an array.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder set(String key, Array value);

            /**
             * Set an array of structs.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setStructs(String key, List<Struct> value);

            /**
             * Set an array of strings.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setStrings(String key, List<String> value);

            /**
             * Set an array of longs.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setLongs(String key, List<Long> value);

            /**
             * Set an array of doubles.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setDoubles(String key, List<Double> value);

            /**
             * Set an array of numbers (such as {@link java.math.BigDecimal}).
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setNumbers(String key, List<BigDecimal> value);

            /**
             * Set an array of booleans.
             *
             * @param key   key to set
             * @param value value to assign to the key
             * @return updated instance (this instance)
             */
            Builder setBooleans(String key, List<Boolean> value);
        }
    }

    /**
     * A HSON value (may of types of {@link io.helidon.metadata.hson.Hson.Type}).
     *
     * @param <T> type of the value
     */
    public sealed interface Value<T> permits HsonValues.StringValue,
                                             HsonValues.NumberValue,
                                             HsonValues.BooleanValue,
                                             HsonValues.NullValue,
                                             Struct,
                                             Hson.Array {
        /**
         * Write the HSON value.
         *
         * @param writer writer to write to
         */
        void write(PrintWriter writer);

        /**
         * Value.
         *
         * @return the value
         */
        T value();

        /**
         * Type of this value.
         *
         * @return type of this value
         */
        Type type();

        /**
         * Get this parsed value as an array.
         *
         * @return this value as an array
         * @throws HsonException in case this value is not of type
         *                       {@link io.helidon.metadata.hson.Hson.Type#ARRAY}
         */
        default Array asArray() {
            if (type() != Type.ARRAY) {
                throw new HsonException("Attempting to read value of type " + type() + " as an array");
            }

            return (Array) this;
        }

        /**
         * Get this parsed value as a struct.
         *
         * @return this value as a struct
         * @throws HsonException in case this value is not of type
         *                       {@link io.helidon.metadata.hson.Hson.Type#STRUCT}
         */
        default Struct asStruct() {
            if (type() != Type.STRUCT) {
                throw new HsonException("Attempting to get value of type " + type() + " as a Struct");
            }

            return (Struct) this;
        }
    }

    /**
     * A representation of HSON array.
     * HSON array is an array of values (of any type).
     */
    public sealed interface Array extends Value<List<Value<?>>> permits HsonArray {
        /**
         * Create an empty JArray.
         *
         * @return empty array
         */
        static Array create() {
            return HsonArray.create();
        }

        /**
         * Create a new array of HSON values.
         *
         * @param values list of values
         * @return a new array
         */
        static Array create(List<? extends Value<?>> values) {
            return HsonArray.create(values);
        }

        /**
         * Create a new array of Strings.
         *
         * @param strings String list
         * @return a new string array
         */
        static Array createStrings(List<String> strings) {
            return HsonArray.createStrings(strings);
        }

        /**
         * Create a new array of Numbers.
         *
         * @param numbers {@link java.math.BigDecimal} list
         * @return a new number array
         */
        static Array createNumbers(List<BigDecimal> numbers) {
            return HsonArray.createNumbers(numbers);
        }

        /**
         * Create a new array of booleans.
         *
         * @param booleans boolean list
         * @return a new boolean array
         */
        static Array createBooleans(List<Boolean> booleans) {
            return HsonArray.createBooleans(booleans);
        }

        /**
         * Create a new array of Numbers from long values.
         *
         * @param values long numbers
         * @return a new number array
         */
        static Array create(long... values) {
            return HsonArray.create(values);
        }

        /**
         * Create a new array of Numbers from int values.
         *
         * @param values int numbers
         * @return a new number array
         */
        static Array create(int... values) {
            return HsonArray.create(values);
        }

        /**
         * Create a new array of Numbers from double values.
         *
         * @param values double numbers
         * @return a new number array
         */
        static Array create(double... values) {
            return HsonArray.create(values);
        }

        /**
         * Create a new array of Numbers from float values.
         *
         * @param values float numbers
         * @return a new number array
         */
        static Array create(float... values) {
            return HsonArray.create(values);
        }

        /**
         * Assume this is an array of strings, and return the list.
         *
         * @return all string values of this array, except for nulls
         * @throws HsonException in case not all elements of this array are strings (or nulls)
         */
        List<String> getStrings();

        /**
         * Assume this is an array of booleans, and return the list.
         *
         * @return all boolean values of this array, except for nulls
         * @throws HsonException in case not all elements of this array are booleans (or nulls)
         */
        List<Boolean> getBooleans();

        /**
         * Assume this is an array of numbers, and return the list.
         *
         * @return all big decimal values of this array, except for nulls
         * @throws HsonException in case not all elements of this array are numbers (or nulls)
         */
        List<BigDecimal> getNumbers();

        /**
         * Assume this is an array of structs, and return the list.
         *
         * @return all struct values of this array, except for nulls
         * @throws HsonException in case not all elements of this array are structs (or nulls)
         */
        List<Struct> getStructs();
    }
}
