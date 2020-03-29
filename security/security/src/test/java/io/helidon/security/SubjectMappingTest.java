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
package io.helidon.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SubjectMappingProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test {@link io.helidon.security.spi.SubjectMappingProvider}.
 */
public class SubjectMappingTest {
    public static final String INTENTIONAL_FAILURE = "Intentional failure";
    public static final String CUSTOM_GRANT_TYPE = "custom-grant-type";
    public static final String CUSTOM_GRANT = "custom-grant";
    public static final String MAPPER_ORIGIN = "my-mapper";
    private static SecurityContext contextWithMapper;

    @BeforeAll
    static void initClass() {
        Security security = Security
                .builder()
                .addProvider(new Atn())
                .addProvider(new Mapper())
                .build();

        contextWithMapper = security.createContext(UUID.randomUUID().toString());
    }

    @Test
    void testNoMapping() {
        Security security = Security
                .builder()
                .addProvider(new Atn())
                .build();

        SecurityContext context = security.createContext(UUID.randomUUID().toString());

        context.env(SecurityEnvironment.builder()
                               .path("jarda")
                               .build());

        AuthenticationResponse authenticate = context.authenticate();

        assertThat(authenticate.user(), not(Optional.empty()));

        Subject subject = authenticate.user().get();
        assertThat(subject.principal().getName(), is("jarda"));
        assertThat(Security.getRoles(subject), is(Set.of()));
        assertThat(subject.grantsByType(CUSTOM_GRANT_TYPE), is(List.of()));
    }

    @Test
    void testUserMapping() {
        contextWithMapper.env(SecurityEnvironment.builder()
                                         .path("jarda")
                                         .build());

        AuthenticationResponse authenticate = contextWithMapper.authenticate();

        assertThat(authenticate.user(), not(Optional.empty()));

        Subject subject = authenticate.user().get();
        assertThat(subject.principal().getName(), is("jarda"));

        assertThat(Security.getRoles(subject), is(Set.of("jarda_role")));
        List<Role> roleGrants = subject.grants(Role.class);
        assertThat("There should be exactly one role granted", roleGrants.size(), is(1));
        Role role = roleGrants.get(0);
        assertThat(role.getName(), is("jarda_role"));
        assertThat(role.origin(), is(MAPPER_ORIGIN));

        assertThat(subject.grantsByType("custom-grant-type"), not(List.of()));
        List<Grant> customGrants = subject.grantsByType(CUSTOM_GRANT_TYPE);
        assertThat("There should be exactly one role granted", customGrants.size(), is(1));
        Grant grant = customGrants.get(0);
        assertThat(grant.getName(), is(CUSTOM_GRANT));
        assertThat(grant.origin(), is(MAPPER_ORIGIN));
    }

    @Test
    void testFailure() {
        contextWithMapper.env(SecurityEnvironment.builder()
                                         .path("fail")
                                         .build());

        AuthenticationResponse response = contextWithMapper.authenticate();

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description(), is(Optional.of(INTENTIONAL_FAILURE)));
    }

    private static class Mapper implements SubjectMappingProvider {
        @Override
        public CompletionStage<AuthenticationResponse> map(ProviderRequest providerRequest,
                                                           AuthenticationResponse previousResponse) {
            return CompletableFuture.completedFuture(buildResponse(providerRequest, previousResponse));
        }

        private AuthenticationResponse buildResponse(ProviderRequest providerRequest,
                                                     AuthenticationResponse previousResponse) {

            Optional<Subject> userSubject = providerRequest.subject();
            Optional<Subject> serviceSubject = providerRequest.service();

            if (userSubject.isPresent()) {
                return mapSubject(userSubject.get());
            }

            if (serviceSubject.isPresent()) {
                return mapSubject(serviceSubject.get());
            }

            return AuthenticationResponse.failed("No subject to map!!!");
        }

        private AuthenticationResponse mapSubject(Subject subject) {
            return AuthenticationResponse.success(Subject.builder()
                                                          .update(subject)
                                                          .addGrant(Role.builder()
                                                                            .name(subject.principal().getName() + "_role")
                                                                            .origin(MAPPER_ORIGIN)
                                                                            .build())
                                                          .addGrant(Grant.builder()
                                                                            .name(CUSTOM_GRANT)
                                                                            .type(CUSTOM_GRANT_TYPE)
                                                                            .origin(MAPPER_ORIGIN)
                                                                            .build())
                                                          .build());
        }
    }

    private static class Atn implements AuthenticationProvider {
        @Override
        public CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest) {
            return CompletableFuture.completedFuture(buildResponse(providerRequest));
        }

        private AuthenticationResponse buildResponse(ProviderRequest providerRequest) {
            return providerRequest.env().path()
                    .map(path -> {
                        switch (path) {
                        case "fail":
                            return AuthenticationResponse.failed(INTENTIONAL_FAILURE);
                        case "abstain":
                            return AuthenticationResponse.abstain();
                        case "service":
                            return AuthenticationResponse.successService(Subject.create(Principal.create("serviceIdentity")));
                        default:
                            return AuthenticationResponse.success(Principal.create(path));
                        }
                    })
                    .orElse(AuthenticationResponse.abstain());
        }
    }
}
