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

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;

/**
 * Response from Vault, always expects a JSON entity.
 */
public abstract class VaultResponse extends ApiEntityResponse {
    private final String requestId;

    /**
     * Create a new response from a builder.
     *
     * @param builder builder with response entity
     */
    protected VaultResponse(ApiEntityResponse.Builder<?, ? extends VaultResponse, JsonObject> builder) {
        super(builder);
        this.requestId = builder.entity().getString("request_id");
    }

    /**
     * Request ID as understood by Vault. May differ from {@link #requestId()}.
     *
     * @return valut request ID
     */
    public String vaultRequestId() {
        return requestId;
    }
}
