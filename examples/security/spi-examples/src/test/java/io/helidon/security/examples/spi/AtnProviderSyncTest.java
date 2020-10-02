/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AtnProviderSync}.
 */
public class AtnProviderSyncTest {
    private static final String VALUE = "aValue";
    private static final int SIZE = 16;

    @Test
    public void testAbstain() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();
        EndpointConfig ep = EndpointConfig.create();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtnProviderSync provider = new AtnProviderSync();

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.ABSTAIN));
    }

    @Test
    public void testAnnotationSuccess() {
        AtnProviderSync.AtnAnnot annot = new AtnProviderSync.AtnAnnot() {
            @Override
            public String value() {
                return VALUE;
            }

            @Override
            public int size() {
                return SIZE;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return AtnProviderSync.AtnAnnot.class;
            }
        };

        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();

        SecurityLevel level = SecurityLevel.create("mock")
                .withClassAnnotations(Map.of(AtnProviderSync.AtnAnnot.class, List.of(annot)))
                .build();

        EndpointConfig ep = EndpointConfig.builder()
                .securityLevels(List.of(level))
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        testSuccess(request);
    }

    @Test
    public void testCustomObjectSuccess() {
        AtnProviderSync.AtnObject obj = new AtnProviderSync.AtnObject();
        obj.setSize(SIZE);
        obj.setValue(VALUE);

        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();
        EndpointConfig ep = EndpointConfig.builder()
                .customObject(AtnProviderSync.AtnObject.class, obj)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        testSuccess(request);
    }

    @Test
    public void testConfigSuccess() {
        Config config = Config.create(
                ConfigSources.create(Map.of("value", VALUE,
                                                             "size", String.valueOf(SIZE)))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();
        EndpointConfig ep = EndpointConfig.builder()
                .config("atn-object", config)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);


        testSuccess(request);
    }

    @Test
    public void testFailure() {
        Config config = Config.create(
                ConfigSources.create(Map.of("atn-object.size", String.valueOf(SIZE)))
        );

        SecurityContext context = mock(SecurityContext.class);
        when(context.user()).thenReturn(Optional.empty());
        when(context.service()).thenReturn(Optional.empty());

        SecurityEnvironment se = SecurityEnvironment.create();
        EndpointConfig ep = EndpointConfig.builder()
                .config("atn-object", config)
                .build();

        ProviderRequest request = mock(ProviderRequest.class);
        when(request.securityContext()).thenReturn(context);
        when(request.env()).thenReturn(se);
        when(request.endpointConfig()).thenReturn(ep);

        AtnProviderSync provider = new AtnProviderSync();

        AuthenticationResponse response = provider.syncAuthenticate(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
    }

    @Test
    public void integrationTest() {
        Security security = Security.builder()
                .addProvider(new AtnProviderSync())
                .build();

        // this part is usually done by container integration component
        // in Jersey you have access to security context through annotations
        // in Web server you have access to security context through context
        SecurityContext context = security.createContext("unit-test");
        context.endpointConfig(EndpointConfig.builder()
                                          .customObject(AtnProviderSync.AtnObject.class,
                                                        AtnProviderSync.AtnObject.from(VALUE, SIZE)));
        AuthenticationResponse response = context.authenticate();

        validateResponse(response);
    }

    private void validateResponse(AuthenticationResponse response) {
        assertThat(response.status(), is(SecurityResponse.SecurityStatus.SUCCESS));
        Optional<Subject> maybeuser = response.user();

        maybeuser.ifPresentOrElse(user -> {
            assertThat(user.principal().id(), is(VALUE));
            Set<String> roles = Security.getRoles(user);
            assertThat(roles.size(), is(1));
            assertThat(roles.iterator().next(), is("role_" + SIZE));
        }, () -> fail("User should have been returned"));
    }

    private void testSuccess(ProviderRequest request) {
        AtnProviderSync provider = new AtnProviderSync();

        AuthenticationResponse response = provider.syncAuthenticate(request);
        validateResponse(response);
    }
}
