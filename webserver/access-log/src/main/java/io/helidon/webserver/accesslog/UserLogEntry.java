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

import java.security.Principal;
import java.util.Optional;

import io.helidon.common.security.SecurityContext;

/**
 * Access log entry for security username.
 * The username has a value only on successful authentication.
 * If there is no security configured, or the authentication fails, username is not available.
 */
public final class UserLogEntry extends AbstractLogEntry {
    private UserLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new user log entry.
     *
     * @return a new access log entry for username
     *
     * @see io.helidon.webserver.accesslog.AccessLogConfig.Builder#addEntry(AccessLogEntry)
     * @see AccessLogConfig.Builder#addEntry(AccessLogEntry)
     */
    public static UserLogEntry create() {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected String doApply(AccessLogContext context) {
        Optional<SecurityContext> maybeContext = context.serverRequest()
                .context()
                .get(SecurityContext.class);

        if (maybeContext.isEmpty()) {
            return NOT_AVAILABLE;
        }

        SecurityContext<Principal> securityContext = maybeContext.get();

        return securityContext.userPrincipal()
                .map(Principal::getName)
                .orElse(NOT_AVAILABLE);
    }

    /**
     * Fluent API builder for {@link UserLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<UserLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public UserLogEntry build() {
            return new UserLogEntry(this);
        }
    }
}
