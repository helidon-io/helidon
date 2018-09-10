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
package io.helidon.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.CollectionsHelper;
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

        context.setEnv(SecurityEnvironment.builder()
                               .path("jarda")
                               .build());

        AuthenticationResponse authenticate = context.authenticate();

        assertThat(authenticate.getUser(), not(Optional.empty()));

        Subject subject = authenticate.getUser().get();
        assertThat(subject.getPrincipal().getName(), is("jarda"));
        assertThat(Security.getRoles(subject), is(CollectionsHelper.setOf()));
        assertThat(subject.getGrantsByType(CUSTOM_GRANT_TYPE), is(CollectionsHelper.listOf()));
    }

    @Test
    void testUserMapping() {
        contextWithMapper.setEnv(SecurityEnvironment.builder()
                                         .path("jarda")
                                         .build());

        AuthenticationResponse authenticate = contextWithMapper.authenticate();

        assertThat(authenticate.getUser(), not(Optional.empty()));

        Subject subject = authenticate.getUser().get();
        assertThat(subject.getPrincipal().getName(), is("jarda"));

        assertThat(Security.getRoles(subject), is(CollectionsHelper.setOf("jarda_role")));
        List<Role> roleGrants = subject.getGrants(Role.class);
        assertThat("There should be exactly one role granted", roleGrants.size(), is(1));
        Role role = roleGrants.get(0);
        assertThat(role.getName(), is("jarda_role"));
        assertThat(role.getOrigin(), is(MAPPER_ORIGIN));

        assertThat(subject.getGrantsByType("custom-grant-type"), not(CollectionsHelper.listOf()));
        List<Grant> customGrants = subject.getGrantsByType(CUSTOM_GRANT_TYPE);
        assertThat("There should be exactly one role granted", customGrants.size(), is(1));
        Grant grant = customGrants.get(0);
        assertThat(grant.getName(), is(CUSTOM_GRANT));
        assertThat(grant.getOrigin(), is(MAPPER_ORIGIN));
    }

    @Test
    void testFailure() {
        contextWithMapper.setEnv(SecurityEnvironment.builder()
                                         .path("fail")
                                         .build());

        AuthenticationResponse response = contextWithMapper.authenticate();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.getDescription(), is(Optional.of(INTENTIONAL_FAILURE)));
    }

    private static class Mapper implements SubjectMappingProvider {
        @Override
        public CompletionStage<AuthenticationResponse> map(ProviderRequest providerRequest,
                                                           AuthenticationResponse previousResponse) {
            return CompletableFuture.completedFuture(buildResponse(providerRequest, previousResponse));
        }

        private AuthenticationResponse buildResponse(ProviderRequest providerRequest,
                                                     AuthenticationResponse previousResponse) {

            Optional<Subject> userSubject = providerRequest.getSubject();
            Optional<Subject> serviceSubject = providerRequest.getService();

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
                                                                            .name(subject.getPrincipal().getName() + "_role")
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
            return providerRequest.getEnv().getPath()
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
