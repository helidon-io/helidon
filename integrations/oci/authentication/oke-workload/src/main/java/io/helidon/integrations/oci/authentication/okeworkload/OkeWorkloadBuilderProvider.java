/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.authentication.okeworkload;

import java.net.URI;
import java.util.function.Supplier;

import io.helidon.integrations.oci.OciConfig;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider.OkeWorkloadIdentityAuthenticationDetailsProviderBuilder;

/**
 * OKE Workload  builder provider, uses the
 * {@link OkeWorkloadIdentityAuthenticationDetailsProviderBuilder}.
 */
@Service.Provider
class OkeWorkloadBuilderProvider implements Supplier<OkeWorkloadIdentityAuthenticationDetailsProviderBuilder> {
    private final OciConfig config;

    OkeWorkloadBuilderProvider(OciConfig config) {
        this.config = config;
    }

    @Override
    public OkeWorkloadIdentityAuthenticationDetailsProviderBuilder get() {
        var builder = OkeWorkloadIdentityAuthenticationDetailsProvider.builder()
                .timeoutForEachRetry((int) config.atnTimeout().toMillis());

        config.imdsBaseUri()
                .map(URI::toString)
                .ifPresent(builder::metadataBaseUrl);

        return builder;
    }

}
