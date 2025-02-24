/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.authentication.instance;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.OciConfig;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@Weight(Weighted.DEFAULT_WEIGHT + 10)
@Service.Provider
class MockedInstancePrincipalBuilderProvider extends InstancePrincipalBuilderProvider
        implements Supplier<InstancePrincipalsAuthenticationDetailsProviderBuilder> {
    static final String INSTANCE_PRINCIPAL_INSTANTIATION_MESSAGE = "Instance Principal has been instantiated";
    private String metadataBaseUrl = null;
    private String tenancyID = null;
    private String federationEndpoint = null;

    MockedInstancePrincipalBuilderProvider(OciConfig config) {
        super(config);
    }

    @Override
    InstancePrincipalsAuthenticationDetailsProviderBuilder getBuilder() {
        // Mock the InstancePrincipalsAuthenticationDetailsProviderBuilder
        final InstancePrincipalsAuthenticationDetailsProviderBuilder builder =
                mock(InstancePrincipalsAuthenticationDetailsProviderBuilder.class);

        doAnswer(invocation -> {
            throw new IllegalArgumentException(INSTANCE_PRINCIPAL_INSTANTIATION_MESSAGE);
        }).when(builder).build();

        // Process metadataBaseUrl
        doAnswer(invocation -> {
            metadataBaseUrl = invocation.getArgument(0);
            return null;
        }).when(builder).metadataBaseUrl(any());
        doAnswer(invocation -> {
            return metadataBaseUrl;
        }).when(builder).getMetadataBaseUrl();

        // Process federationEndpoint
        doAnswer(invocation -> {
            federationEndpoint = invocation.getArgument(0);
            return null;
        }).when(builder).federationEndpoint(any());
        doAnswer(invocation -> {
            return federationEndpoint;
        }).when(builder).getFederationEndpoint();

        // Process tenancyId
        doAnswer(invocation -> {
            tenancyID = invocation.getArgument(0);
            return null;
        }).when(builder).tenancyId(any());
        doAnswer(invocation -> {
            return tenancyID;
        }).when(builder).getTenancyId();

        return builder;
    }
}
