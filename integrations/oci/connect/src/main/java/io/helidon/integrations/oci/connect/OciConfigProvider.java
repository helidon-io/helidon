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

import java.util.Optional;

import io.helidon.common.reactive.Single;

public interface OciConfigProvider {
    /**
     * Get the current signature data.
     *
     * @return current signature data
     */
    OciSignatureData signatureData();

    /**
     * Current OCI region.
     *
     * @return OCI region, such as {@code eu-frankfurt-1}
     */
    String region();

    /**
     * OCID of the tenancy.
     *
     * @return tenancy OCID
     */
    String tenancyOcid();

    /**
     * OCI domain to use. If not available REST API will use the default or configured domain to
     * call REST services.
     *
     * @return current OCI domain if avialable
     */
    default Optional<String> domain() {
        return Optional.empty();
    }

    /**
     * Refresh may be used for providers that can be reloaded.
     * The method is called when an invocation fails (for the first time) with a 401 exception.
     *
     * @return future with signature data, if no refresh was done, the instance will be the same as when
     *  {@link #signatureData()} was called.
     */
    default Single<OciSignatureData> refresh() {
        return Single.just(signatureData());
    }
}
