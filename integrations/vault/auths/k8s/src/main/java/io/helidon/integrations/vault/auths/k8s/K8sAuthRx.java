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

package io.helidon.integrations.vault.auths.k8s;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.AuthMethod;

/**
 * Kubernetes authentication method API.
 *
 * See <a href="https://www.vaultproject.io/docs/auth/kubernetes">https://www.vaultproject.io/docs/auth/kubernetes</a>.
 * When used to authenticate against a Vault from a k8s pod, there is no need to use this API directly.
 */
public interface K8sAuthRx {
    /**
     * Kubernetes authentication method.
     */
    AuthMethod<K8sAuthRx> AUTH_METHOD = AuthMethod.create(K8sAuthRx.class, "kubernetes", "kubernetes");

    /**
     * Service token type.
     */
    String TYPE_SERVICE = "service";
    /**
     * Batch token type.
     */
    String TYPE_BATCH = "batch";
    /**
     * Default token type.
     */
    String TYPE_DEFAULT = "default";

    /**
     * Registers a role in the auth method. Role types have specific entities that can perform login operations against this
     * endpoint. Constraints specific to the role type must be set on the role. These are applied to the authenticated entities
     * attempting to login.
     *
     * @param request create role request
     * @return when the role is created
     */
    Single<CreateRole.Response> createRole(CreateRole.Request request);

    /**
     * Deletes the previously registered role.
     *
     * @param request delete role request
     * @return when the role is deleted
     */
    Single<DeleteRole.Response> deleteRole(DeleteRole.Request request);

    /**
     * Fetch a token. This endpoint takes a signed JSON Web Token (JWT) and a role name for some entity. It verifies the JWT
     * signature to authenticate that entity and then authorizes the entity for the given role.
     *
     * @param request login request
     * @return login response
     */
    Single<Login.Response> login(Login.Request request);

    /**
     * Configure this authentication method.
     * <p>
     * The Kubernetes auth method validates service account JWTs and verifies their existence with the Kubernetes
     * TokenReview API. This endpoint configures the public key used to validate the JWT signature and the necessary
     * information to access the Kubernetes API.
     * <p>
     * <b>Caveats</b>
     * <p>
     * If Vault is running in a Kubernetes Pod, the kubernetes_ca_cert and token_reviewer_jwt parameters will automatically
     * default to the local CA cert (/var/run/secrets/kubernetes.io/serviceaccount/ca.crt) and local service account JWT
     * (/var/run/secrets/kubernetes.io/serviceaccount/token). This behavior may be disabled by setting disable_local_ca_jwt to
     * true.
     *
     * When Vault is running in a non-Kubernetes environment, either kubernetes_ca_cert or pem_keys must be set by the user.
     * @param request request to configure
     * @return when configured
     */
    Single<ConfigureK8s.Response> configure(ConfigureK8s.Request request);
}
