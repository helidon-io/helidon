/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.SecurityTest;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;

/**
 * Provider authenticating and propagating security based on resource type.
 */
public class ResourceBasedProvider implements AuthenticationProvider, AuthorizationProvider, OutboundSecurityProvider {
    static AuthenticationResponse service(String aService) {
        return AuthenticationResponse.successService(Principal.create(aService));
    }

    static AuthenticationResponse success(String name) {
        return AuthenticationResponse.success(Principal.create(name));
    }

    @Override
    public CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest) {
        SecurityEnvironment env = providerRequest.env();

        return CompletableFuture.completedFuture(env.abacAttribute("resourceType")
                                                         .map(String::valueOf)
                                                         .map(resource -> {
                                                             switch (resource) {
                                                             case "jack":
                                                                 return success("resource-jack");
                                                             case "jill":
                                                                 return success("resource-jill");
                                                             case "service":
                                                                 return service("resource-aService");
                                                             case "fail":
                                                                 return AuthenticationResponse
                                                                         .failed("resource-Intentional fail");
                                                             case "successFinish":
                                                                 return AuthenticationResponse.builder()
                                                                         .status(SecurityResponse.SecurityStatus.SUCCESS_FINISH)
                                                                         .user(SecurityTest.SYSTEM)
                                                                         .build();
                                                             case "abstain":
                                                                 return AuthenticationResponse.abstain();
                                                             default:
                                                                 if (resource.startsWith("atz")) {
                                                                     return success("atz");
                                                                 }
                                                                 return AuthenticationResponse.failed("resource-Invalid request");
                                                             }
                                                         }).orElse(AuthenticationResponse.abstain()));
    }

    @Override
    public CompletionStage<AuthorizationResponse> authorize(ProviderRequest context) {
        SecurityEnvironment env = context.env();

        return CompletableFuture.completedFuture(env.abacAttribute("resourceType")
                                                         .map(String::valueOf)
                                                         .map(resource -> {
                                                             switch (resource) {
                                                             case "atz/permit":
                                                                 return AuthorizationResponse.permit();
                                                             case "atz/deny":
                                                                 return AuthorizationResponse.deny();
                                                             case "atz/abstain":
                                                                 return AuthorizationResponse.abstain();
                                                             case "atz/fail":
                                                                 return AuthorizationResponse.builder()
                                                                         .status(SecurityResponse.SecurityStatus.FAILURE)
                                                                         .description("Intentional failure")
                                                                         .build();
                                                             default:
                                                                 return AuthorizationResponse.permit();
                                                             }
                                                         }).orElse(AuthorizationResponse.abstain()));
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {

        return providerRequest
                .env()
                .abacAttribute("resourceType")
                .isPresent();
    }

    @Override
    public CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                                      SecurityEnvironment outboundEnv,
                                                                      EndpointConfig outboundConfig) {

        return CompletableFuture.completedFuture(providerRequest
                                                         .env()
                                                         .abacAttribute("resourceType")
                                                         .map(String::valueOf)
                                                         .map(resource -> {
                                                             switch (resource) {
                                                             case "jack":
                                                                 return OutboundSecurityResponse
                                                                         .withHeaders(Map.of("resource",
                                                                                             List.of("resource-jack")));
                                                             case "jill":
                                                                 return OutboundSecurityResponse
                                                                         .withHeaders(Map.of("resource",
                                                                                             List.of("resource-jill")));
                                                             case "service":
                                                                 return OutboundSecurityResponse
                                                                         .withHeaders(Map.of("resource",
                                                                                             List.of("resource-aService")));
                                                             case "fail":
                                                                 return OutboundSecurityResponse.builder()
                                                                         .status(SecurityResponse.SecurityStatus.FAILURE)
                                                                         .description("resource-Intentional fail").build();
                                                             case "successFinish":
                                                                 return OutboundSecurityResponse.builder()
                                                                         .status(SecurityResponse.SecurityStatus.SUCCESS_FINISH)
                                                                         .build();
                                                             case "abstain":
                                                                 return OutboundSecurityResponse.abstain();
                                                             default:
                                                                 return OutboundSecurityResponse.builder()
                                                                         .status(SecurityResponse.SecurityStatus.FAILURE)
                                                                         .description("resource-Invalid request").build();
                                                             }
                                                         }).orElse(OutboundSecurityResponse.abstain()));
    }
}
