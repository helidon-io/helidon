/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.provider.httpauth;

import java.util.Collection;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;

/**
 * Store of users for resolving httpauth and digest authentication.
 */
public interface UserStore {
    /**
     * Get user based on login.
     *
     * @param login login of the user (as obtained from request)
     * @return User information (empty if user is not found)
     */
    Optional<User> getUser(String login);

    /**
     * Representation of a single user.
     */
    interface User {
        /**
         * Get login name.
         *
         * @return login of the user
         */
        String getLogin();

        /**
         * Get password of the user.
         * The password must be provided in clear text, as we may need to create a digest based on the password
         * and other (variable) values for digest authentication.
         *
         * @return password
         */
        char[] getPassword();

        /**
         * Get set of roles the user is in.
         *
         * @return roles of this user (or empty if not supported).
         */
        default Collection<String> getRoles() {
            return CollectionsHelper.setOf();
        }
    }
}
