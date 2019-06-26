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

/**
 * Access log entry for host (IP) values.
 */
public final class HostLogEntry extends AbstractLogEntry {
    private HostLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new host log entry.
     *
     * @return a new access log entry for host
     * @see io.helidon.webserver.accesslog.AccessLogSupport.Builder#add(AccessLogEntry)
     */
    public static HostLogEntry create() {
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
    protected String doApply(AccessLogContext context) {
        String remoteAddress = context.serverRequest().remoteAddress();
        return (null == remoteAddress) ? NOT_AVAILABLE : remoteAddress;
    }

    /**
     * A fluent API builder for {@link io.helidon.webserver.accesslog.HostLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<HostLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public HostLogEntry build() {
            return new HostLogEntry(this);
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }
}
