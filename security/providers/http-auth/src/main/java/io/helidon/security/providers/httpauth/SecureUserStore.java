/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpauth;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Store of users for resolving HTTP basic authentication.
 * This implementation does not require exposing stored passwords to the provider itself.
 * Keep in mind that HTTP Basic authentication is unsafe even when combined with SSL and should only be used for
 * local testing and demos.
 */
@FunctionalInterface
public interface SecureUserStore {
    /**
     * Get user based on login.
     * The returned user may not be populated - {@link User#roles()}
     * is never called before {@link User#isPasswordValid(char[])}.
     * Also the missing user and user with wrong password are treated the same - so if your implementation
     * cannot decide whether a user exists until the password is checked, you can delay that decision and just
     * return {@code false} from {@link User#isPasswordValid(char[])} for both cases (e.g. invalid user and invalid password).
     *
     * @param login login of the user (as obtained from request)
     * @return User information (empty if user is not found)
     */
    Optional<User> user(String login);

    /**
     * Representation of a single user.
     */
    interface User {
        /**
         * Get login name.
         *
         * @return login of the user
         */
        String login();

        /**
         * Check if the password is valid.
         * Used by basic authentication.
         *
         * @param password password of the user as obtained via basic authentication
         * @return {@code true} if password is valid for this user, {@code false} otherwise
         */
        boolean isPasswordValid(char[] password);

        /**
         * Get set of roles the user is in.
         *
         * @return roles of this user (or empty if not supported).
         */
        default Collection<String> roles() {
            return Set.of();
        }

    }
}
