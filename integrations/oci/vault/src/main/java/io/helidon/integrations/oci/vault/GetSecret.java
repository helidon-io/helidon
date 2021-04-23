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

package io.helidon.integrations.oci.vault;

import io.helidon.integrations.common.rest.ApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;

/**
 * Request and response for getting secret metadata.
 */
public final class GetSecret {
    private GetSecret() {
    }

    /**
     * Request object. Can be configured with additional headers, query parameters etc.
     */
    public static class Request extends OciRequestBase<Request> {
        private String secretId;

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
         * Create a new request for a secret OCID.
         *
         * @param secretOcid secret OCID
         * @return a new request
         *
         * @see #secretId(String)
         */
        public static Request create(String secretOcid) {
            return builder().secretId(secretOcid);
        }

        /**
         * Secret OCID to get.
         *
         * @param secretId secret OCID
         * @return updated request
         */
        public Request secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        String secretId() {
            if (secretId == null) {
                throw new ApiException("secretId is mandatory in GetSecret.Request");
            }
            return secretId;
        }
    }
}
