/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver.accesslog;

import java.text.Normalizer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Common log entry features.
 */
public abstract class AbstractLogEntry implements AccessLogEntry {
    /**
     * Default limit for the number of characters written.
     */
    public static final int DEFAULT_MAX_LENGTH = 512;
    static final char QUOTES = '"';
    static final char SPACE = ' ';

    private final Function<String, String> padding;
    private final Function<String, String> sanitization;
    private final int maxLength;

    /**
     * Create a new instance using a builder.
     *
     * @param builder builder that extends {@link io.helidon.webserver.accesslog.AbstractLogEntry.Builder}
     */
    protected AbstractLogEntry(Builder<?, ?> builder) {
        this.padding = builder.padding;
        this.sanitization = builder.sanitizationFunction();
        this.maxLength = builder.maxLength;
    }

    /**
     * Apply the log entry, adds padding to the log entry and then calls
     * {@link #doApply(AccessLogContext)}.
     *
     * @param context context with access to information useful for access log entries
     * @return formatted log entry
     */
    @Override
    public String apply(AccessLogContext context) {
        return pad(maxLength(sanitize(doApply(context))));
    }

    /**
     * Apply maximal length limitation.
     *
     * @param toLimit sanitized string
     * @return shortened string
     */
    protected String maxLength(String toLimit) {
        if (toLimit.length() > maxLength) {
            if (toLimit.charAt(0) == '!') {
                return toLimit.substring(0, maxLength);
            }

            return '!' + toLimit.substring(0, maxLength - 1);
        }
        return toLimit;
    }

    /**
     * Apply configured sanitization.
     *
     * @param toSanitize string to sanitize
     * @return sanitized string
     */
    protected String sanitize(String toSanitize) {
        return sanitization.apply(toSanitize);
    }

    /**
     * Apply configured padding.
     * @param toPad string to pad
     * @return padded string
     */
    protected String pad(String toPad) {
        return padding.apply(toPad);
    }

    /**
     * Apply the "raw" log entry. The result will go through common formatting,
     * such as padding if configured.
     *
     * @param context context with access to information useful for access log entries
     * @return log entry
     * @see #NOT_AVAILABLE
     */
    protected abstract String doApply(AccessLogContext context);

    /**
     * A fluent API builder for {@link io.helidon.webserver.accesslog.AbstractLogEntry}.
     * Extend this class to implement your own log entries.
     *
     * @param <R> The type of your log entry
     * @param <T> The type of your builder
     */
    protected abstract static class Builder<R extends AbstractLogEntry, T extends Builder<R, ?>>
            implements io.helidon.common.Builder<R> {
        private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^\\p{Print}]", Pattern.UNICODE_CHARACTER_CLASS);

        @SuppressWarnings("unchecked")
        private final T builder = (T) this;

        private Function<String, String> padding = Function.identity();
        private boolean sanitize = true;
        private int maxLength = DEFAULT_MAX_LENGTH;

        /**
         * Apply no padding on the output.
         * This is the default.
         *
         * @return updated builder instance
         */
        public T noPad() {
            this.padding = Function.identity();
            return builder;
        }

        /**
         * Apply right padding to fill the defined length.
         * Padding uses spaces.
         * If log entry length is bigger than padding length, log entry is returned as is
         *
         * @param length length to pad to
         * @return updated builder instance
         */
        public T rPad(int length) {
            this.padding = (
                    orig -> {
                        if (orig.length() >= length) {
                            return orig;
                        }
                        StringBuilder builder = new StringBuilder(length);

                        builder.append(orig);
                        for (int i = 0; i < (length - orig.length()); i++) {
                            builder.append(' ');
                        }

                        return builder.toString();
                    });
            return builder;
        }

        /**
         * Apply left padding to fill the defined length.
         * Padding uses spaces.
         * If log entry length is bigger than padding length, log entry is returned as is
         *
         * @param length length to pad to
         * @return updated builder instance
         */
        public T lPad(int length) {
            this.padding = (
                    orig -> {
                        if (orig.length() >= length) {
                            return orig;
                        }
                        StringBuilder builder = new StringBuilder(length);

                        for (int i = 0; i < (length - orig.length()); i++) {
                            builder.append(' ');
                        }

                        builder.append(orig);

                        return builder.toString();
                    });
            return builder;
        }

        /**
         * Configure output sanitization.
         * In case the sanitized output differs from original value,
         * the output will be prefixed with an exclamation mark.
         *
         * @param sanitize whether to sanitize output (for log entries that are based on text from HTTP request).
         * @return updated builder instance
         */
        public T sanitize(boolean sanitize) {
            this.sanitize = sanitize;
            return builder;
        }

        /**
         * Configure maximal length of the output written.
         * Modified output will be prefixed with an exclamation mark.
         *
         * @param maxLength maximal length to write
         * @return updated builder instance
         */
        public T maxLength(int maxLength) {
            this.maxLength = maxLength;
            return builder;
        }

        private Function<String, String> sanitizationFunction() {
            if (sanitize) {
                return s -> {
                    String result = Normalizer.normalize(s, Normalizer.Form.NFKC);
                    result = SANITIZE_PATTERN.matcher(result).replaceAll("");

                    if (s.equals(result)) {
                        return s;
                    }
                    return "!" + result;
                };
            } else {
                return Function.identity();
            }
        }
    }
}
