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

    public static class Request extends OciRequestBase<Request> {
        private String secretId;

        private Request() {
        }

        public static Request builder() {
            return new Request();
        }

        public static Request create(String secretOcid) {
            return builder().secretId(secretOcid);
        }

        public Request secretId(String secretId) {
            this.secretId = secretId;
            return this;
        }

        public String secretId() {
            if (secretId == null) {
                throw new ApiException("secretId is mandatory in GetSecret.Request");
            }
            return secretId;
        }
    }
}
