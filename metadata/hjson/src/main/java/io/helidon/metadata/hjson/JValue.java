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

package io.helidon.metadata.hjson;

import java.io.InputStream;
import java.io.PrintWriter;

/**
 * A JSON value (may of types of {@link io.helidon.metadata.hjson.JType}).
 *
 * @param <T> type of the value
 */
public sealed interface JValue<T> permits JValues.StringValue,
                                          JValues.NumberValue,
                                          JValues.BooleanValue,
                                          JValues.NullValue,
                                          JObject,
                                          JArray {
    /**
     * Read a JSON value from an input stream.
     *
     * @param stream input stream to read JSON from
     * @return a parsed value, either non-array of type {@link io.helidon.metadata.hjson.JType#OBJECT},
     *         or an array
     * @see #asObject()
     * @see #asArray()
     */
    static JValue<?> read(InputStream stream) {
        return JParser.parse(stream);
    }

    /**
     * Write the JSON value.
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
    JType type();

    /**
     * Get an object array from this parsed value.
     *
     * @return object array, or this object as an array
     * @throws io.helidon.metadata.hjson.JException in case this object is not of type
     *                                              {@link io.helidon.metadata.hjson.JType#OBJECT}
     */
    default JArray asArray() {
        if (type() != JType.ARRAY) {
            throw new JException("Attempting to read object of type " + type() + " as an array");
        }

        return (JArray) this;
    }

    /**
     * Get an object from this parsed value.
     *
     * @return this value as an object
     * @throws io.helidon.metadata.hjson.JException in case this object is not of type
     *                                              {@link io.helidon.metadata.hjson.JType#OBJECT}
     */
    default JObject asObject() {
        if (type() != JType.OBJECT) {
            throw new JException("Attempting to get object of type " + type() + " as an Object");
        }

        return (JObject) this;
    }
}
