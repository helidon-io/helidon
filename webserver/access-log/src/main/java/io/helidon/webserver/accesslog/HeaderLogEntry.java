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

import java.util.List;

/**
 * Access log entry for header values.
 */
public final class HeaderLogEntry extends AbstractLogEntry {
    private final String headerName;

    private HeaderLogEntry(Builder builder) {
        super(builder);
        this.headerName = builder.headerName;
    }

    /**
     * Create a header log entry for a specified header name with default configuration.
     *
     * @param headerName name of HTTP header to print to the access log
     * @return a new header log entry
     */
    public static HeaderLogEntry create(String headerName) {
        return builder(headerName).build();
    }

    /**
     * Create a fluent API builder for a header log entry.
     *
     * @param headerName name of HTTP header to print to the access log
     * @return a fluent API builder
     */
    public static Builder builder(String headerName) {
        return new Builder(headerName);
    }

    @Override
    protected String doApply(AccessLogContext context) {
        List<String> values = context.serverRequest().headers().all(headerName);
        if (values.isEmpty()) {
            return NOT_AVAILABLE;
        }
        if (values.size() == 1) {
            return QUOTES + values.get(0) + QUOTES;
        }

        return QUOTES + String.join(",", values) + QUOTES;
    }

    /**
     * Fluent API builder for {@link io.helidon.webserver.accesslog.HeaderLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<HeaderLogEntry, Builder> {
        private final String headerName;

        private Builder(String headerName) {
            this.headerName = headerName;
        }

        @Override
        public HeaderLogEntry build() {
            return new HeaderLogEntry(this);
        }
    }
}
