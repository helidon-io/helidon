/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.examples.spi;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Example of a simplistic outbound security provider.
 */
public class OutboundProviderSync extends SynchronousProvider implements OutboundSecurityProvider {
    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {

        // let's just add current user's id as a custom header, otherwise do nothing
        return providerRequest.getContext()
                .getUser()
                .map(Subject::getPrincipal)
                .map(Principal::getName)
                .map(name -> OutboundSecurityResponse
                        .withHeaders(CollectionsHelper.mapOf("X-AUTH-USER", CollectionsHelper.listOf(name))))
                .orElse(OutboundSecurityResponse.abstain());
    }
}
