/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import io.helidon.http.HttpPrologue;

/**
 * Access log entry for request line.
 * Creates an entry {@code "METHOD path HTTP/version"}, such as
 * {@code "GET /greet HTTP/1.1"}.
 */
public final class RequestLineLogEntry extends AbstractLogEntry {
    private RequestLineLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new request line entry.
     *
     * @return a new access log entry for request line
     * @see AccessLogConfig.Builder#addEntry(AccessLogEntry)
     */
    public static RequestLineLogEntry create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String doApply(AccessLogContext context) {
        HttpPrologue prologue = context.serverRequest().prologue();

        return QUOTES
                // HTTP Method
                + prologue.method().text()
                + SPACE
                // Path
                + prologue.uriPath().rawPath()
                + SPACE
                // HTTP version
                + prologue.protocol() + "/" + prologue.protocolVersion()
                + QUOTES;
    }

    /**
     * A fluent API builder for {@link RequestLineLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<RequestLineLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public RequestLineLogEntry build() {
            return new RequestLineLogEntry(this);
        }
    }
}
