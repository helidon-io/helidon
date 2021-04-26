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

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * Vault authentication method for AppRole.
 */
public interface AppRoleAuthRx {
    /**
     * Authentication method.
     */
    AuthMethod<AppRoleAuthRx> AUTH_METHOD = AuthMethod.create(AppRoleAuthRx.class, "approle", "approle");

    /**
     * Creates a new AppRole or updates an existing AppRole. There
     * can be one or more constraints enabled on the role. It is required to have at least one of them enabled while creating
     * or updating a role.
     *
     * @param appRoleRequest Create AppRole request
     * @return when the AppRole gets created
     */
    Single<CreateAppRole.Response> createAppRole(CreateAppRole.Request appRoleRequest);

    /**
     * Deletes an existing AppRole from the method with full control of request.
     *
     * @param request delete AppRole request
     * @return when the AppRole gets deleted
     */
    Single<DeleteAppRole.Response> deleteAppRole(DeleteAppRole.Request request);

    /**
     * Reads the RoleID of an existing AppRole.
     *
     * @param appRole name of the AppRole
     * @return role ID
     * @see #readRoleId(io.helidon.integrations.vault.auths.approle.ReadRoleId.Request)
     */
    default Single<Optional<String>> readRoleId(String appRole) {
        return readRoleId(ReadRoleId.Request.create(appRole))
                .map(it -> it.entity().map(ReadRoleId.Response::roleId));
    }

    /**
     * Reads the RoleID of an existing AppRole with full control of request and response.
     *
     * @param request request with name of the AppRole
     * @return role ID
     */
    Single<VaultOptionalResponse<ReadRoleId.Response>> readRoleId(ReadRoleId.Request request);

    /**
     * Generates and issues a new SecretID on an existing AppRole. Similar to tokens, the response will also contain a
     * secretIdAccessor value which can be used to read the properties of the SecretID without divulging the SecretID itself,
     * and also to delete the SecretID from the AppRole.
     *
     * @param request generate secret ID request
     * @return a new secret id response
     */
    Single<GenerateSecretId.Response> generateSecretId(GenerateSecretId.Request request);

    /**
     * Destroy an AppRole secret ID.
     *
     * @param request destroy secret ID request
     * @return when the id gets destroyed
     */
    Single<DestroySecretId.Response> destroySecretId(DestroySecretId.Request request);

    /**
     * Issues a Vault token based on the presented credentials.
     *
     * @param request login request
     * @return Login response (with Vault token)
     */
    Single<Login.Response> login(Login.Request request);
}
