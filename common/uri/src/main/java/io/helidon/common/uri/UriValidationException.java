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

package io.helidon.common.uri;

import java.util.Objects;

import static io.helidon.common.uri.UriValidator.encode;
import static io.helidon.common.uri.UriValidator.print;

/**
 * A URI validation exception.
 * <p>
 * This type provides access to the invalid value that is not cleaned, through {@link #invalidValue()}.
 * The exception message is cleaned and can be logged and returned to users ({@link #getMessage()}).
 *
 * @see #invalidValue()
 */
public class UriValidationException extends IllegalArgumentException {
    private final Segment segment;
    private final char[] invalidValue;

    /**
     * Create a new validation exception that uses a descriptive message and the failed chars.
     * The message provided will be appended with cleaned invalid value in double quotes.
     *
     * @param segment      segment that caused this exception
     * @param invalidValue value that failed validation
     * @param message      descriptive message
     */
    public UriValidationException(Segment segment, char[] invalidValue, String message) {
        super(toMessage(invalidValue, message));

        this.segment = segment;
        this.invalidValue = invalidValue;
    }

    /**
     * Create a new validation exception that uses a descriptive message and the failed chars.
     *
     * @param segment      segment that caused this exception
     * @param invalidValue value that failed validation
     * @param validated    a validated section of the full value
     * @param message      descriptive message
     */
    UriValidationException(Segment segment, char[] invalidValue, char[] validated, String message) {
        super(toMessage(invalidValue, validated, message));

        this.segment = segment;
        this.invalidValue = invalidValue;
    }

    /**
     * Create a new validation exception that uses a descriptive message and the failed chars.
     *
     * @param segment      segment that caused this exception
     * @param invalidValue value that failed validation
     * @param validated    a validated section of the full value
     * @param message      descriptive message
     * @param index        index in the {@code validated} array that failed
     * @param c            character that was invalid
     */
    UriValidationException(Segment segment, char[] invalidValue, char[] validated, String message, int index, char c) {
        super(toMessage(invalidValue, validated, message, index, c));

        this.segment = segment;
        this.invalidValue = invalidValue;
    }

    /**
     * Create a new validation exception that uses a descriptive message and the failed chars.
     *
     * @param segment      segment that caused this exception
     * @param invalidValue value that failed validation
     * @param message      descriptive message
     * @param index        index in the {@code invalidValue} array that failed
     * @param c            character that was invalid
     */
    UriValidationException(Segment segment, char[] invalidValue, String message, int index, char c) {
        super(toMessage(invalidValue, message, index, c));

        this.segment = segment;
        this.invalidValue = invalidValue;
    }

    /**
     * The value that did not pass validation.
     * This value is as it was received over the network, so it is not safe to log or return to the user!
     *
     * @return invalid value that failed validation
     */
    public char[] invalidValue() {
        return invalidValue;
    }

    /**
     * Segment that caused this validation exception.
     *
     * @return segment of the URI
     */
    public Segment segment() {
        return segment;
    }

    private static String toMessage(char[] value, String message) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(message);

        if (value.length == 0) {
            return message;
        }
        return message + ": " + encode(value);
    }

    private static String toMessage(char[] value, char[] validated, String message) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(message);
        Objects.requireNonNull(validated);

        if (validated.length == 0) {
            if (value.length == 0) {
                return message;
            }
            return message + ". Value: " + encode(value);
        }
        if (value.length == 0) {
            return message + ": " + encode(validated);
        }
        return message + ": " + encode(validated)
                + ". Value: " + encode(value);
    }

    private static String toMessage(char[] value, char[] validated, String message, int index, char c) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(validated);
        Objects.requireNonNull(message);

        return message + ": " + encode(validated) + ", index: " + index
                + ", char: " + print(c)
                + ". Value: " + encode(value);
    }

    private static String toMessage(char[] value, String message, int index, char c) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(message);

        return message + ": " + encode(value) + ", index: " + index
                + ", char: " + print(c);
    }

    /**
     * Segment of the URI that caused this validation failure.
     */
    public enum Segment {
        /**
         * URI Scheme.
         */
        SCHEME("Scheme"),
        /**
         * URI Host.
         */
        HOST("Host"),
        /**
         * URI Path.
         */
        PATH("Path"),
        /**
         * URI Query.
         */
        QUERY("Query"),
        /**
         * URI Fragment.
         */
        FRAGMENT("Fragment");
        private final String name;

        Segment(String name) {
            this.name = name;
        }

        /**
         * Human-readable text that describes this segment.
         *
         * @return segment text
         */
        public String text() {
            return name;
        }
    }
}
