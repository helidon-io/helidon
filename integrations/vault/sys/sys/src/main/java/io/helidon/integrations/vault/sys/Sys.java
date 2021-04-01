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

import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.Engine;

/**
 * Blocking APIs for Sys operations on Vault. Methods block the calling thread.
 * DO NOT use this API in reactive environment, always use {@link io.helidon.integrations.vault.sys.SysRx}.
 * <p>
 * This class is intended for use in blocking environments, such as CDI (where it can be injected)
 *  or in blocking server environment, where it can be obtained through {@link #create(SysRx)}.
 *
 */
public interface Sys {
    /**
     * Create a new instance of blocking Vault Sys operations from the reactive instance.
     * Handle with caution, as all methods on the returned instance block the calling thread, and as such
     * are NOT SUITABLE FOR REACTIVE usage.
     *
     * @param reactiveSys reactive vault Sys operations, as obtained from
     * {@link io.helidon.integrations.vault.Vault#sys(io.helidon.integrations.vault.SysApi)}
     * @return a new blocking Sys API
     */
    static Sys create(SysRx reactiveSys) {
        return new SysImpl(reactiveSys);
    }

    /**
     * Enable (mount) a secret engine on a default path.
     *
     * @param engine engine to enable
     * @return when the engine is enabled
     */
    default EnableEngine.Response enableEngine(Engine<?> engine) {
        return enableEngine(EnableEngine.Request.builder().engine(engine));
    }

    /**
     * Enable (mount) a secret engine with custom configuration.
     *
     * @param request request for mount operation
     * @return when the engine is enabled
     */
    EnableEngine.Response enableEngine(EnableEngine.Request request);

    /**
     * Disable (unmount) a secret engine from default path.
     * @param engine to disable
     * @return when the engine is disabled
     */
    default DisableEngine.Response disableEngine(Engine<?> engine) {
        return disableEngine(DisableEngine.Request.builder()
                                     .engine(engine));
    }

    /**
     * Disable (unmount) a secret engine from specific path.
     * @param path mount path
     * @return when the engine is disabled
     */
    default DisableEngine.Response disableEngine(String path) {
        return disableEngine(DisableEngine.Request.builder()
                                     .path(path));
    }

    DisableEngine.Response disableEngine(DisableEngine.Request request);

    /**
     * Enable an authentication method on default path.
     *
     * @param authMethod authentication method to enable
     * @return when the method is enabled
     */
    default EnableAuth.Response enableAuth(AuthMethod<?> authMethod) {
        return enableAuth(EnableAuth.Request.builder()
                                  .auth(authMethod));
    }

    /**
     * Enable an authentication method on custom path or with additional configuration.
     *
     * @param request mount request
     * @return when the method is enabled
     */
    EnableAuth.Response enableAuth(EnableAuth.Request request);

    /**
     * Disable an authentication method.
     *
     * @param path path of the method
     * @return when method is disabled
     */
    default DisableAuth.Response disableAuth(String path) {
        return disableAuth(DisableAuth.Request.builder()
                                   .path(path));
    }

    DisableAuth.Response disableAuth(DisableAuth.Request request);

    /**
     * Create a policy.
     *
     * @param name name of the policy
     * @param policy policy document
     * @return when policy is created
     */
    default CreatePolicy.Response createPolicy(String name, String policy) {
        return createPolicy(CreatePolicy.Request.create(name, policy));
    }

    CreatePolicy.Response createPolicy(CreatePolicy.Request request);

    /**
     * Delete a policy.
     *
     * @param name name of the policy
     * @return when policy is deleted
     */
    default DeletePolicy.Response deletePolicy(String name) {
        return deletePolicy(DeletePolicy.Request.create(name));
    }

    DeletePolicy.Response deletePolicy(DeletePolicy.Request request);
}
