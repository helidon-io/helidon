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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultResponse;
import io.helidon.integrations.vault.VaultToken;

import static io.helidon.integrations.vault.VaultUtil.arrayToList;

/**
 * Response returning a token.
 */
public abstract class TokenResponse extends VaultResponse {
    private final VaultToken token;
    private final String accessor;
    private final List<String> policies;
    private final List<String> tokenPolicies;
    private final Map<String, String> metadata;
    private final String entityId;
    private final String tokenType;
    private final boolean orphan;

    protected TokenResponse(ApiEntityResponse.Builder<?, ? extends VaultResponse, JsonObject> builder) {
        super(builder);

        JsonObject auth = builder.entity().getJsonObject("auth");

        this.accessor = auth.getString("accessor");
        this.policies = arrayToList(auth.getJsonArray("policies"));
        this.tokenPolicies = arrayToList(auth.getJsonArray("token_policies"));
        this.metadata = toMap(auth, "metadata");
        this.entityId = auth.getString("entity_id");
        this.tokenType = auth.getString("token_type");
        this.orphan = auth.getBoolean("orphan");
        this.token = VaultToken.builder()
                .token(auth.getString("client_token"))
                .leaseDuration(Duration.ofSeconds(auth.getInt("lease_duration")))
                .renewable(auth.getBoolean("renewable"))
                .build();
    }

    /**
     * Token that was received.
     *
     * @return the token
     */
    public VaultToken token() {
        return token;
    }

    /**
     * Accessor.
     *
     * @return accessor
     */
    public String accessor() {
        return accessor;
    }

    /**
     * List of policy names.
     *
     * @return policies
     */
    public List<String> policies() {
        return policies;
    }

    /**
     * List of token policy names.
     *
     * @return token policies
     */
    public List<String> tokenPolicies() {
        return tokenPolicies;
    }

    /**
     * Metadata. When a token is created with metadata attached, it is available through this method.
     *
     * @return key/values of metadata
     */
    public Map<String, String> metadata() {
        return metadata;
    }

    public String entityId() {
        return entityId;
    }

    /**
     * Type of the token.
     *
     * @return token type
     *
     * @see TokenAuth#TYPE_SERVICE
     * @see TokenAuth#TYPE_BATCH
     */
    public String tokenType() {
        return tokenType;
    }

    /**
     * Whether the token is orphan (no parent).
     *
     * @return {@code true} if orphan
     */
    public boolean orphan() {
        return orphan;
    }
}
