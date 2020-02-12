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
 * Access log entry for user id. This always returns {@value NOT_AVAILABLE}.
 */
public final class UserIdLogEntry implements AccessLogEntry {
    private static final UserIdLogEntry INSTANCE = new UserIdLogEntry();

    private UserIdLogEntry() {
    }

    /**
     * Create a new access log entry for user id.
     * @return an entry that always considers user id to be undefined
     * @see io.helidon.webserver.accesslog.AccessLogSupport.Builder#add(AccessLogEntry)
     * @see io.helidon.webserver.accesslog.UserLogEntry
     */
    public static UserIdLogEntry create() {
        // does nothing, no need to create a new instance
        return INSTANCE;
    }

    @Override
    public String apply(AccessLogContext context) {
        return NOT_AVAILABLE;
    }
}
