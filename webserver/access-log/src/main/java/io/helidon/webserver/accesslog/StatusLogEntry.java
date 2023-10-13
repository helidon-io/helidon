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

/**
 * Access log entry for HTTP status.
 */
public final class StatusLogEntry extends AbstractLogEntry {
    private StatusLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new status log entry.
     *
     * @return a new access log entry for HTTP status
     * @see AccessLogConfig.Builder#addEntry(AccessLogEntry)
     */
    public static StatusLogEntry create() {
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
        return String.valueOf(context.serverResponse().status().code());
    }

    /**
     * A fluent API builder for {@link StatusLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<StatusLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public StatusLogEntry build() {
            return new StatusLogEntry(this);
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }
}
