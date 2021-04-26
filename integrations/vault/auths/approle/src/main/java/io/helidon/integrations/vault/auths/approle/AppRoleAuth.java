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

import java.util.Optional;

import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Vault authentication method for AppRole.
 * All methods block the current thread. This implementation is not suitable for reactive programming.
 * Use {@link io.helidon.integrations.vault.auths.approle.AppRoleAuthRx} in reactive code.
 */
public interface AppRoleAuth {
    /**
     * Create AppRole blocking API from its reactive counterpart.
     *
     * @param reactive AppRole reactive API
     * @return AppRole blocking API
     */
    static AppRoleAuth create(AppRoleAuthRx reactive) {
        return new AppRoleAuthImpl(reactive);
    }

    /**
     * Creates a new AppRole or updates an existing AppRole. There
     * can be one or more constraints enabled on the role. It is required to have at least one of them enabled while creating
     * or updating a role.
     *
     * @param appRoleRequest Create AppRole request
     * @return when the AppRole gets created
     */
    CreateAppRole.Response createAppRole(CreateAppRole.Request appRoleRequest);

    /**
     * Deletes an existing AppRole from the method with full control of request.
     *
     * @param request delete AppRole request
     * @return when the AppRole gets deleted
     */
    DeleteAppRole.Response deleteAppRole(DeleteAppRole.Request request);

    /**
     * Reads the RoleID of an existing AppRole.
     *
     * @param appRole name of the AppRole
     * @return role ID
     * @see #readRoleId(io.helidon.integrations.vault.auths.approle.ReadRoleId.Request)
     */
    default Optional<String> readRoleId(String appRole) {
        return readRoleId(ReadRoleId.Request.create(appRole))
                .entity()
                .map(ReadRoleId.Response::roleId);
    }

    /**
     * Reads the RoleID of an existing AppRole with full control of request and response.
     *
     * @param request request with name of the AppRole
     * @return role ID
     */
    VaultOptionalResponse<ReadRoleId.Response> readRoleId(ReadRoleId.Request request);

    /**
     * Generates and issues a new SecretID on an existing AppRole. Similar to tokens, the response will also contain a
     * secretIdAccessor value which can be used to read the properties of the SecretID without divulging the SecretID itself,
     * and also to delete the SecretID from the AppRole.
     *
     * @param request generate secret ID request
     * @return a new secret id response
     */
    GenerateSecretId.Response generateSecretId(GenerateSecretId.Request request);

    /**
     * Destroy an AppRole secret ID.
     *
     * @param request destroy secret ID request
     * @return when the id gets destroyed
     */
    DestroySecretId.Response destroySecretId(DestroySecretId.Request request);

    /**
     * Issues a Vault token based on the presented credentials.
     *
     * @param request login request
     * @return Login response (with Vault token)
     */
    Login.Response login(Login.Request request);
}
