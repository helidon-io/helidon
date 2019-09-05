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

package io.helidon.security.providers.httpauth;

import java.util.Collection;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;

/**
 * Store of users for resolving httpauth and digest authentication.
 * This implementation does not require to provide passwords. This is a more secure approach.
 * Keep in mind that HTTP Basic authentication is an unsafe protection, and even when combined with SSL, it still has some
 * severe issues.
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
            return CollectionsHelper.setOf();
        }

        /**
         * Digest authentication requires a hash of username, realm and password.
         * As password should not be revealed by systems, this is to provide the HA1 (from Digest Auth terminology)
         * based on the known (public) information combined with the secret information available to user store only (password).
         * <p>
         * ha1 algorithm ({@code unq} stands for "unquoted value")
         * <pre>
         *    ha1 = md5(a1);
         *    a1 = unq(username-value) ":" unq(realm-value) ":" passwd
         * </pre>
         *
         * @param realm configured realm
         * @param algorithm algorithm of the hash (current only MD5 supported by Helidon)
         * @return a digest to use for validation of incoming request
         */
        default Optional<String> digestHa1(String realm, HttpDigest.Algorithm algorithm) {
            return Optional.empty();
        }
    }
}
