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

package io.helidon.integrations.vault.secrets.database;

import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.VaultApiException;

/**
 * Database credentials.
 */
public interface DbCredentials extends Secret {
    /**
     * Name of the database user to use.
     *
     * @return username
     */
    default String username() {
        return value("username")
                .orElseThrow(() -> new VaultApiException("Username is not present in credentials. Available values names: "
                                                                 + values().keySet()));
    }

    /**
     * Password of the database user.
     *
     * @return password
     */
    default String password() {
        return value("password")
                .orElseThrow(() -> new VaultApiException("Password is not present in credentials. Available values names: "
                                                                 + values().keySet()));
    }
}
