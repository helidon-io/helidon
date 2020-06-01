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

package io.helidon.security.providers.abac;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;

import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link io.helidon.security.providers.abac.AbacProvider}.
 */
public class AbacProviderTest {
    @Test
    public void testMissingValidator() {
        AbacProvider provider = AbacProvider.create();
        Attrib1 attrib = Mockito.mock(Attrib1.class);
        doReturn(Attrib1.class).when(attrib).annotationType();

        SecurityLevel level = SecurityLevel.create("mock")
                .withClassAnnotations(Map.of(Attrib1.class, List.of(attrib)))
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(List.of(level))
                .build();

        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        when(request.endpointConfig()).thenReturn(ec);

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description(), not(Optional.empty()));
        response.description()
                .ifPresent(desc -> {
                    assertThat(desc, containsString("Attrib1 attribute annotation is not supported"));
                });
    }

    @Test
    public void testMissingRoleValidator() {
        AbacProvider provider = AbacProvider.create();
        // this must be implicitly considered an attribute annotation
        RolesAllowed attrib = Mockito.mock(RolesAllowed.class);
        doReturn(RolesAllowed.class).when(attrib).annotationType();

        SecurityLevel level = SecurityLevel.create("mock")
                .withClassAnnotations(Map.of(RolesAllowed.class, List.of(attrib)))
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(List.of(level))
                .build();

        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        when(request.endpointConfig()).thenReturn(ec);

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description(), not(Optional.empty()));
        response.description()
                .ifPresent(desc -> assertThat(desc, containsString("RolesAllowed attribute annotation is not supported")));
    }

    @Test
    public void testExistingValidatorFail() {
        AbacProvider provider = AbacProvider.builder()
                .addValidator(new Attrib1Validator())
                .build();

        Attrib1 attrib = Mockito.mock(Attrib1.class);
        when(attrib.value()).thenReturn(false);
        doReturn(Attrib1.class).when(attrib).annotationType();

        SecurityLevel level = SecurityLevel.create("mock")
                .withClassAnnotations(Map.of(Attrib1.class, List.of(attrib)))
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(List.of(level))
                .build();

        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        when(request.endpointConfig()).thenReturn(ec);

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.status(), is(SecurityResponse.SecurityStatus.FAILURE));
        assertThat(response.description(), not(Optional.empty()));
        response.description()
                .ifPresent(desc -> assertThat(desc, containsString("Intentional unit test failure")));
    }

    @Test
    public void testExistingValidatorSucceed() {
        AbacProvider provider = AbacProvider.builder()
                .addValidator(new Attrib1Validator())
                .build();

        Attrib1 attrib = Mockito.mock(Attrib1.class);
        when(attrib.value()).thenReturn(true);
        doReturn(Attrib1.class).when(attrib).annotationType();

        SecurityLevel level = SecurityLevel.create("mock")
                .withClassAnnotations(Map.of(Attrib1.class, List.of(attrib)))
                .build();

        EndpointConfig ec = EndpointConfig.builder()
                .securityLevels(List.of(level))
                .build();

        ProviderRequest request = Mockito.mock(ProviderRequest.class);
        when(request.endpointConfig()).thenReturn(ec);

        AuthorizationResponse response = provider.syncAuthorize(request);

        assertThat(response.description().orElse("Attrib1 value is true, so the authorization should succeed"),
                   response.status(),
                   is(SecurityResponse.SecurityStatus.SUCCESS));
    }
}
