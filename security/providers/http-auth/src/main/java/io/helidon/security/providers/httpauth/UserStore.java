/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Optional;

/**
 * Store of users for resolving httpauth and digest authentication.
 *
 * @deprecated This store is designed for POC - e.g. no need for better security. You can use
 * {@link io.helidon.security.providers.httpauth.SecureUserStore} instead.
 */
@FunctionalInterface
@Deprecated
public interface UserStore extends SecureUserStore {
    /**
     * Representation of a single user.
     */
    interface User extends SecureUserStore.User {
        /**
         * Get password of the user.
         * The password must be provided in clear text, as we may need to create a digest based on the password
         * and other (variable) values for digest authentication.
         *
         * @return password
         */
        char[] password();

        @Override
        default boolean isPasswordValid(char[] password) {
            return Arrays.equals(password, password());
        }

        @Override
        default Optional<String> digestHa1(String realm, HttpDigest.Algorithm algorithm) {
            return Optional.of(DigestToken.ha1(algorithm, realm, login(), password()));
        }
    }
}
