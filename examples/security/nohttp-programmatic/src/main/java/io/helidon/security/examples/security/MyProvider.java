/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.examples.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Sample provider.
 */
class MyProvider extends SynchronousProvider implements AuthenticationProvider, AuthorizationProvider, OutboundSecurityProvider {

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        //get username and password
        List<String> headers = providerRequest.env().headers().getOrDefault("authorization", List.of());
        if (headers.isEmpty()) {
            return AuthenticationResponse.failed("No authorization header");
        }

        String header = headers.get(0);
        if (header.toLowerCase().startsWith("basic ")) {
            String base64 = header.substring(6);
            String unamePwd = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            int index = unamePwd.indexOf(':');
            if (index > 0) {
                String name = unamePwd.substring(0, index);
                String pwd = unamePwd.substring(index + 1);
                if ("aUser".equals(name)) {
                    //authenticate
                    Principal principal = Principal.create(name);
                    Role roleGrant = Role.create("theRole");

                    Subject subject = Subject.builder()
                            .principal(principal)
                            .addGrant(roleGrant)
                            .addPrivateCredential(MyPrivateCreds.class, new MyPrivateCreds(name, pwd.toCharArray()))
                            .build();

                    return AuthenticationResponse.success(subject);
                }
            }
        }

        return AuthenticationResponse.failed("User not found");
    }

    @Override
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        if ("CustomResourceType"
                .equals(providerRequest.env().abacAttribute("resourceType").orElseThrow(() -> new IllegalArgumentException(
                        "Resource type is a required parameter")))) {
            //supported resource
            return providerRequest.securityContext()
                    .user()
                    .map(Subject::principal)
                    .map(Principal::getName)
                    .map("aUser"::equals)
                    .map(correct -> {
                        if (correct) {
                            return AuthorizationResponse.permit();
                        }
                        return AuthorizationResponse.deny();
                    })
                    .orElse(AuthorizationResponse.deny());
        }

        return AuthorizationResponse.deny();
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEndpointConfig) {

        return providerRequest.securityContext()
                .user()
                .flatMap(subject -> subject.privateCredential(MyPrivateCreds.class))
                .map(myPrivateCreds -> OutboundSecurityResponse.builder()
                        .requestHeader("Authorization", authHeader(myPrivateCreds))
                        .build()
                ).orElse(OutboundSecurityResponse.abstain());
    }

    private String authHeader(MyPrivateCreds privCreds) {
        String creds = privCreds.name + ":" + new String(privCreds.password);
        return "basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    private static class MyPrivateCreds {
        private final String name;
        private final char[] password;

        private MyPrivateCreds(String name, char[] password) {
            this.name = name;
            this.password = password;
        }

        @Override
        public String toString() {
            return "MyPrivateCreds: " + name;
        }
    }
}
