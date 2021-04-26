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

package io.helidon.integrations.vault.auths.approle;

import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * Base request class for AppRole requests.
 *
 * @param <T> type of the request
 */
abstract class AppRoleRequestBase<T extends AppRoleRequestBase<T>> extends VaultRequest<T> {
    private String roleName;

    /**
     * AppRole name.
     * @param roleName AppRole name
     * @return updated request
     */
    public T roleName(String roleName) {
        this.roleName = roleName;
        return me();
    }

    String roleName() {
        if (roleName == null) {
            throw new VaultApiException(getClass().getSimpleName() + " role name must be defined");
        }
        return roleName;
    }
}
