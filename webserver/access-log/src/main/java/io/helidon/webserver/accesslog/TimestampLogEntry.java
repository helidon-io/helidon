/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.time.format.DateTimeFormatter;

/**
 * Access log entry for timestamp.
 * Default time format is {@value DEFAULT_FORMAT}.
 */
public final class TimestampLogEntry extends AbstractLogEntry {
    private static final String DEFAULT_FORMAT = "'['dd/MMM/YYYY:HH:mm:ss ZZZ']'";
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_FORMAT);
    private final DateTimeFormatter formatter;

    private TimestampLogEntry(Builder builder) {
        super(builder);
        this.formatter = builder.formatter;
    }

    /**
     * Create a new Timestamp log entry.
     *
     * @return a new access log entry for Timestamp
     * @see io.helidon.webserver.accesslog.AccessLogSupport.Builder#add(AccessLogEntry)
     */
    public static TimestampLogEntry create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder().defaults();
    }

    @Override
    public String doApply(AccessLogContext context) {
        return formatter.format(context.requestDateTime());
    }

    /**
     * A fluent API builder for {@link io.helidon.webserver.accesslog.HostLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<TimestampLogEntry, Builder> {
        private DateTimeFormatter formatter = DEFAULT_FORMATTER;

        private Builder() {
        }

        @Override
        public TimestampLogEntry build() {
            return new TimestampLogEntry(this);
        }

        /**
         * Configure a date time formatter to use with this log entry.
         * Default format is {@value #DEFAULT_FORMAT}.
         *
         * @param formatter date time format to use, should contain text separaters (such as {@code []}).
         * @return updated builder instance
         */
        public Builder formatter(DateTimeFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }
}

