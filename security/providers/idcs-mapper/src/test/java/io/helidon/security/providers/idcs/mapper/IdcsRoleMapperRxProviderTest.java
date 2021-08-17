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

package io.helidon.security.providers.idcs.mapper;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Single;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.oidc.common.OidcConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.junit.jupiter.api.Assertions.fail;

class IdcsRoleMapperRxProviderTest {
    private static TestProvider provider;

    @BeforeAll
    static void prepareProvider() {
        IdcsRoleMapperRxProvider.Builder<?> builder = IdcsRoleMapperRxProvider.builder();
        builder.oidcConfig(OidcConfig.builder()
                                   .oidcMetadataWellKnown(false)
                                   .clientId("client-id")
                                   .clientSecret("client-secret")
                // intentionally wrong IP address, so this immediately fails when an attempt is done to cnnect
                                   .identityUri(URI.create("https://497.497.497.497/identity/uri"))
                                   .tokenEndpointUri(URI.create("https://497.497.497.497/token/endpoint/uri"))
                                   .authorizationEndpointUri(URI.create("https://497.497.497.497/authorization/endpoint/uri"))
                                   .build())
                .roleCache(EvictableCache.<String, List<Grant>>builder()
                                   .maxSize(2)
                                   .build());
        provider = new TestProvider(builder);
    }

    @Test
    void testCacheUsed() {
        ProviderRequest mock = Mockito.mock(ProviderRequest.class);
        String username = "test-user";
        AuthenticationResponse response = provider.map(mock,
                                                   AuthenticationResponse.builder()
                                                           .user(Subject.builder()
                                                                         .principal(Principal.create(username))
                                                                         .build())
                                                           .build())
                .toCompletableFuture()
                .join();

        Subject subject = response.user()
                .get();

        List<Role> grants = subject.grants(Role.class);

        assertThat(grants, iterableWithSize(5));
        assertThat(grants, hasItems(Role.create("fixed"), Role.create(username), Role.create("additional-fixed")));
        Role counted = findCounted(grants);
        Role additionalCounted = findAdditionalCounted(grants);
        response = provider.map(mock,
                                AuthenticationResponse.builder()
                                        .user(Subject.builder()
                                                      .principal(Principal.create(username))
                                                      .build())
                                        .build())
                .toCompletableFuture()
                .join();
        grants = response.user().get().grants(Role.class);
        assertThat(grants, iterableWithSize(5));
        Role counted2 = findCounted(grants);
        assertThat("Expecting the same role, as it should have been cached", counted2, is(counted));
        Role additionalCounted2 = findAdditionalCounted(grants);
        assertThat("Additional roles should not be cached", additionalCounted2, not(additionalCounted));
    }

    private Role findCounted(List<Role> grants) {
        for (Role grant : grants) {
            if (grant.getName().startsWith("counted_")) {
                return grant;
            }
        }
        fail("Could not find counted role in grants: " + grants);
        return null;
    }

    private Role findAdditionalCounted(List<Role> grants) {
        for (Role grant : grants) {
            if (grant.getName().startsWith("additional_")) {
                return grant;
            }
        }
        fail("Could not find additional counted role in grants: " + grants);
        return null;
    }

    private static final class TestProvider extends IdcsRoleMapperRxProvider {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        private TestProvider(Builder<?> builder) {
            super(builder);
        }

        @Override
        protected CompletionStage<List<? extends Grant>> getGrantsFromServer(Subject subject) {
            String id = subject.principal().id();
            return Single.just(List.of(Role.create("counted_"+ COUNTER.incrementAndGet()),
                                       Role.create("fixed"),
                                       Role.create(id)));
        }

        @Override
        protected CompletionStage<List<? extends Grant>> addAdditionalGrants(Subject subject, List<Grant> idcsGrants) {
            return Single.just(List.of(Role.create("additional_"+ COUNTER.incrementAndGet()),
                                       Role.create("additional-fixed")));
        }
    }
}