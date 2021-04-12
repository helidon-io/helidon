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

package io.helidon.integrations.vault.secrets.pki;

import java.time.Instant;

import javax.json.JsonObject;

import io.helidon.integrations.common.rest.ApiEntityResponse;
import io.helidon.integrations.vault.VaultRequest;
import io.helidon.integrations.vault.VaultResponse;

/**
 * Revoke certificate request and response.
 */
public final class RevokeCertificate {
    private RevokeCertificate() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static final class Request extends VaultRequest<Request> {
        private Request() {
        }

        /**
         * Fluent API builder for configuring a request.
         * The request builder is passed as is, without a build method.
         * The equivalent of a build method is {@link #toJson(javax.json.JsonBuilderFactory)}
         * used by the {@link io.helidon.integrations.common.rest.RestApi}.
         *
         * @return new request builder
         */
        public static Request builder() {
            return new Request();
        }

        /**
         * Serial number of the certificate to revoke.
         *
         * @param serialNumber serial number
         * @return updated request
         */
        public Request serialNumber(String serialNumber) {
            return add("serial_number", serialNumber);
        }
    }

    /**
     * Response object parsed from JSON returned by the {@link io.helidon.integrations.common.rest.RestApi}.
     */
    public static final class Response extends VaultResponse {
        private final Instant revocationTime;

        private Response(Builder builder) {
            super(builder);

            this.revocationTime = Instant.ofEpochSecond(builder.entity()
                                                                .getJsonObject("data")
                                                                .getJsonNumber("revocation_time")
                                                                .longValue());
        }

        static Builder builder() {
            return new Builder();
        }

        /**
         * Revocation instant of the certificate.
         *
         * @return instant the certificate was revoked
         */
        public Instant revocationTime() {
            return revocationTime;
        }

        static final class Builder extends ApiEntityResponse.Builder<Builder, Response, JsonObject> {
            private Builder() {
            }

            @Override
            public Response build() {
                return new Response(this);
            }
        }
    }
}
