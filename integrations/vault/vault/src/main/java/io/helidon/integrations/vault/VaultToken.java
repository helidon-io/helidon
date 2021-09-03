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

/**
 * Vault token implementation.
 */
public final class VaultToken extends VaultTokenBase {

    private VaultToken(Builder builder) {
        super(builder);
    }

    /**
     * Create a new fluent API builder.
     * @return builder to construct new token
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link io.helidon.integrations.vault.VaultToken}.
     */
    public static class Builder extends VaultTokenBase.Builder<Builder, VaultToken> {
        private Builder() {
        }

        @Override
        public VaultToken build() {
            return new VaultToken(this);
        }
    }
}
