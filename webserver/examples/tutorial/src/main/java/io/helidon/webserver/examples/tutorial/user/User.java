/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.tutorial.user;

import io.helidon.webserver.Routing;

/**
 * Represents an immutable user.
 *
 * <p>{@link UserFilter} can be registered on Web Server {@link Routing Routing} to provide valid {@link User}
 * instance on the request context.
 */
public final class User {

    /**
     * Represents an anonymous user.
     */
    public static final User ANONYMOUS = new User();

    private final boolean authenticated;
    private final String alias;
    private final boolean anonymous;

    /**
     * Creates new instance non-anonymous user.
     *
     * @param authenticated an authenticated is {@code true} if this user identity was validated
     * @param alias         an alias represents the name of the user which is visible for others
     */
    User(boolean authenticated, String alias) {
        this.authenticated = authenticated;
        this.alias = alias;
        this.anonymous = false;
    }

    /**
     * Creates an unauthenticated user.
     *
     * @param alias an alias represents the name of the user which is visible for others
     */
    User(String alias) {
        this(false, alias);
    }

    /**
     * Creates an anonymous user instance.
     */
    private User() {
        this.anonymous = true;
        this.authenticated = false;
        this.alias = "anonymous";
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isAnonymous() {
        return anonymous;
    }
}
