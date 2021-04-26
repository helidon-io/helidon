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

package io.helidon.integrations.vault.auths.token;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.AuthMethod;

/**
 * Token authentication method API.
 *
 * See <a href="https://www.vaultproject.io/api-docs/auth/token">https://www.vaultproject.io/api-docs/auth/token</a>
 */
public interface TokenAuthRx {
    /**
     * Token authentication method.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/api-docs/auth/token">https://www.vaultproject.io/api-docs/auth/token</a>
     */
    AuthMethod<TokenAuthRx> AUTH_METHOD = AuthMethod.create(TokenAuthRx.class, "token", "token");

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
     * Create a new child token with default configuration.
     * @return a new token
     */
    default Single<CreateToken.Response> createToken() {
        return createToken(CreateToken.Request.builder());
    }

    /**
     * Create a new orphan token with default configuration.
     * @return a new token
     */
    default Single<CreateToken.Response> createOrphan() {
        return createToken(CreateToken.Request.builder()
                                   .noParent(true));
    }

    /**
     * Create a new token with customized configuration.
     *
     * @param request token request
     * @return a new token
     */
    Single<CreateToken.Response> createToken(CreateToken.Request request);

    /**
     * Renews a lease associated with a token. This is used to prevent the expiration of a token, and the automatic revocation
     * of it. Token renewal is possible only if there is a lease associated with it.
     *
     * @param request with token to renew
     * @return a new token
     */
    Single<RenewToken.Response> renew(RenewToken.Request request);

    /**
     * Revokes a token and all child tokens. When the token is revoked, all dynamic secrets generated with it are also revoked.
     *
     * @param request with token to revoke
     * @return when revocation finishes
     */
    Single<RevokeToken.Response> revoke(RevokeToken.Request request);

    /**
     * Creates (or replaces) the named role. Roles enforce specific behavior when creating tokens that allow token
     * functionality that is otherwise not available or would require sudo/root privileges to access. Role parameters, when
     * set, override any provided options to the create endpoints. The role name is also included in the token path, allowing
     * all tokens created against a role to be revoked using the /sys/leases/revoke-prefix endpoint.
     *
     * @param request token role request
     * @return when creation finishes
     */
    Single<CreateTokenRole.Response> createTokenRole(CreateTokenRole.Request request);

    /**
     * Delete a named token role.
     *
     * @param request with name of the role
     * @return when deleted
     */
    Single<DeleteTokenRole.Response> deleteTokenRole(DeleteTokenRole.Request request);

    /**
     * Revokes a token and orphans all child tokens. When the token is revoked, all dynamic secrets generated with it are also
     * revoked.
     *
     * This is a root protected endpoint.
     *
     * @param request with token to revoke
     * @return when revocation finishes
     */
    Single<RevokeAndOrphanToken.Response> revokeAndOrphan(RevokeAndOrphanToken.Request request);
}
