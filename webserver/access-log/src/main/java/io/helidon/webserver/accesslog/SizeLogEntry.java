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
 * Access log entry for entity size.
 */
public final class SizeLogEntry extends AbstractLogEntry {
    private static final String SIZE_CONTEXT_CLASSIFIER = SizeLogEntry.class.getName() + ".size";

    private SizeLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new size log entry instance.
     *
     * @return a new access log entry for entity size
     * @see AccessLogConfig.Builder#addEntry(AccessLogEntry)
     */
    public static SizeLogEntry create() {
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
        return String.valueOf(context.serverResponse().bytesWritten());
    }

    /**
     * A fluent API builder for {@link SizeLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<SizeLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public SizeLogEntry build() {
            return new SizeLogEntry(this);
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }
}
