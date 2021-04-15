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

package io.helidon.integrations.oci.connect;

import java.security.KeyPair;

import io.helidon.common.reactive.Single;

abstract class OciConfigPrincipalBase {
    private final SessionKeySupplier keySupplier;
    private final FederationClient federationClient;

    protected OciConfigPrincipalBase(Builder<?> builder) {
        this.keySupplier = builder.keySupplier;
        this.federationClient = builder.federationClient;
    }

    protected interface SessionKeySupplier {
        KeyPair keyPair();
        Single<KeyPair> refresh();
    }

    protected interface FederationClient {
        Single<String> securityToken();

        Single<String> refreshSecurityToken();

        Single<String> claim(String claimName);
    }

    /**
     * Fluent API builder base for {@link io.helidon.integrations.oci.connect.OciConfigPrincipalBase}
     * subclasses.
     *
     * @param <B> builder type
     */
    static class Builder<B extends Builder<B>> {
        private SessionKeySupplier keySupplier;
        private FederationClient federationClient;

        protected Builder() {
        }

        Builder<B> keySupplier(SessionKeySupplier keySupplier) {
            this.keySupplier = keySupplier;
            return this;
        }

        Builder<B> federationClient(FederationClient federationClient) {
            this.federationClient = federationClient;
            return this;
        }
    }
}
