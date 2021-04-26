/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault;

/**
 * An authentication method.
 * Supported built-in methods are available as constants.
 *
 * @param <T> type of the API provided by the engine
 */
public interface AuthMethod<T> {
    /**
     * Create an authentication method.
     *
     * @param auth auth API class
     * @param type type of auth
     * @param defaultPath default mount path of this method
     * @param <T> type of auth API
     * @return a new authentication method
     */
    static <T> AuthMethod<T> create(Class<T> auth, String type, String defaultPath) {
        return new AuthMethod<>() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Class<T> apiType() {
                return auth;
            }

            @Override
            public String defaultPath() {
                return defaultPath;
            }
        };
    }

    /**
     * Type of the auth as used in the REST API.
     *
     * @return type of the auth, such as {@code token}
     */
    String type();

    /**
     * Implementation class of this authentication method.
     *
     * @return API class
     * @see Vault#auth(io.helidon.integrations.vault.AuthMethod)
     */
    Class<T> apiType();

    /**
     * Default path the authentication method is mounted to.
     *
     * @return default path
     */
    String defaultPath();
}
