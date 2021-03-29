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

package io.helidon.integrations.vault.sys;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.SysApi;

public interface Sys {
    /**
     * The API of this sys implementation.
     */
    SysApi<Sys> API = SysApi.create(Sys.class);

    /**
     * Enable (mount) a secret engine on a default path.
     *
     * @param engine engine to enable
     * @return when the engine is enabled
     */
    default Single<EnableEngine.Response> enableEngine(Engine<?> engine) {
        return enableEngine(EnableEngine.Request.builder().engine(engine));
    }

    /**
     * Enable (mount) a secret engine with custom configuration.
     *
     * @param request request for mount operation
     * @return when the engine is enabled
     */
    Single<EnableEngine.Response> enableEngine(EnableEngine.Request request);

    /**
     * Disable (unmount) a secret engine from default path.
     * @param engine to disable
     * @return when the engine is disabled
     */
    default Single<DisableEngine.Response> disableEngine(Engine<?> engine) {
        return disableEngine(DisableEngine.Request.builder()
                                     .engine(engine));
    }

    /**
     * Disable (unmount) a secret engine from specific path.
     * @param path mount path
     * @return when the engine is disabled
     */
    default Single<DisableEngine.Response> disableEngine(String path) {
        return disableEngine(DisableEngine.Request.builder()
                                     .path(path));
    }

    Single<DisableEngine.Response> disableEngine(DisableEngine.Request request);

    /**
     * Enable an authentication method on default path.
     *
     * @param authMethod authentication method to enable
     * @return when the method is enabled
     */
    default Single<EnableAuth.Response> enableAuth(AuthMethod<?> authMethod) {
        return enableAuth(EnableAuth.Request.builder()
                                  .auth(authMethod));
    }

    /**
     * Enable an authentication method on custom path or with additional configuration.
     *
     * @param request mount request
     * @return when the method is enabled
     */
    Single<EnableAuth.Response> enableAuth(EnableAuth.Request request);

    /**
     * Disable an authentication method.
     *
     * @param path path of the method
     * @return when method is disabled
     */
    default Single<DisableAuth.Response> disableAuth(String path) {
        return disableAuth(DisableAuth.Request.builder()
                                   .path(path));
    }

    Single<DisableAuth.Response> disableAuth(DisableAuth.Request request);

    /**
     * Create a policy.
     *
     * @param name name of the policy
     * @param policy policy document
     * @return when policy is created
     */
    default Single<CreatePolicy.Response> createPolicy(String name, String policy) {
        return createPolicy(CreatePolicy.Request.create(name, policy));
    }

    Single<CreatePolicy.Response> createPolicy(CreatePolicy.Request request);

    /**
     * Delete a policy.
     *
     * @param name name of the policy
     * @return when policy is deleted
     */
    default Single<DeletePolicy.Response> deletePolicy(String name) {
        return deletePolicy(DeletePolicy.Request.create(name));
    }

    Single<DeletePolicy.Response> deletePolicy(DeletePolicy.Request request);
}
