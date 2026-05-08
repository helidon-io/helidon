/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.RoleMapTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.Jwk;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.junit.jupiter.api.Assertions.fail;

class IdcsRoleMapperProviderTest {
    private static final String ASSERTER_SCHEMA = "urn:ietf:params:scim:schemas:oracle:idcs:Asserter";
    private static final URI IDENTITY_URI = URI.create("https://idcs.example.com/identity/uri");
    private static final URI TOKEN_ENDPOINT_URI = URI.create("https://idcs.example.com/token/endpoint/uri");
    private static final URI AUTHORIZATION_ENDPOINT_URI =
            URI.create("https://idcs.example.com/authorization/endpoint/uri");
    private static TestProvider provider;

    @BeforeAll
    static void prepareProvider() {
        provider = new TestProvider(roleMapperBuilder());
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
                                                           .build());

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
                                        .build());
        grants = response.user().get().grants(Role.class);
        assertThat(grants, iterableWithSize(5));
        Role counted2 = findCounted(grants);
        assertThat("Expecting the same role, as it should have been cached", counted2, is(counted));
        Role additionalCounted2 = findAdditionalCounted(grants);
        assertThat("Additional roles should not be cached", additionalCounted2, not(additionalCounted));
    }

    @Test
    void testProcessRoleRequestReadsHelidonJson() {
        HttpClientRequest request = Mockito.mock(HttpClientRequest.class);
        HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
        JsonObject responseEntity = JsonObject.builder()
                .set("groups", JsonArray.create(JsonObject.builder()
                                                         .set("display", "group-role")
                                                         .set("value", "group-id")
                                                         .set("$ref", "/groups/group-id")
                                                         .build()))
                .set("appRoles", JsonArray.create(JsonObject.builder()
                                                           .set("display", "app-role")
                                                           .set("value", "app-id")
                                                           .set("$ref", "/appRoles/app-id")
                                                           .build()))
                .build();

        Mockito.when(request.submit(Mockito.any())).thenReturn(response);
        Mockito.when(response.status()).thenReturn(Status.OK_200);
        Mockito.when(response.as(JsonObject.class)).thenReturn(responseEntity);

        List<Role> grants = provider.processRoleRequest(request, JsonObject.builder().build(), "test-user")
                .stream()
                .map(Role.class::cast)
                .toList();

        assertThat(grants, iterableWithSize(2));
        assertThat(grants, hasItems(Role.builder()
                                         .name("group-role")
                                         .addAttribute("type", IdcsRoleMapperProviderBase.ROLE_GROUP)
                                         .addAttribute("id", "group-id")
                                         .addAttribute("ref", "/groups/group-id")
                                         .build(),
                                    Role.builder()
                                         .name("app-role")
                                         .addAttribute("type", IdcsRoleMapperProviderBase.ROLE_APPROLE)
                                         .addAttribute("id", "app-id")
                                         .addAttribute("ref", "/appRoles/app-id")
                                         .build()));
    }

    @Test
    void testGetGrantsFromServerSubmitsJsonObjectEntity() {
        CapturingRequestProvider capturingProvider = new CapturingRequestProvider(roleMapperBuilder(), "app-token");

        capturingProvider.getGrantsFromServer(Subject.builder()
                                                     .principal(Principal.create("test-user"))
                                                     .build());

        HttpClientRequest request = capturingProvider.request();
        assertThat(request, notNullValue());
        assertThat(request.resolvedUri().host(), is("idcs.example.com"));
        assertThat(request.resolvedUri().path().path(), is("/identity/uri/admin/v1/Asserter"));
        assertThat(request.headers().contains(HeaderNames.CONTENT_TYPE), is(false));
        assertThat(request.headers().get(HeaderNames.AUTHORIZATION).get(), is("Bearer app-token"));
        assertThat(capturingProvider.subjectName(), is("test-user"));
        assertThat(capturingProvider.entity(), is(instanceOf(JsonObject.class)));

        JsonObject requestEntity = (JsonObject) capturingProvider.entity();
        assertThat(requestEntity.stringValue("mappingAttributeValue").orElseThrow(), is("test-user"));
        assertThat(requestEntity.stringValue("subjectType").orElseThrow(),
                   is(IdcsRoleMapperProviderBase.IDCS_SUBJECT_TYPE_USER));
        assertThat(requestEntity.booleanValue("includeMemberships").orElseThrow(), is(true));
        assertThat(requestEntity.arrayValue("schemas").orElseThrow().get(0).orElseThrow().asString().value(),
                   is(ASSERTER_SCHEMA));
    }

    @Test
    void testNoProtectedRoleMapTracingHookLeaksIntoExportedApi() {
        boolean hasProtectedRoleMapTracingMethod = false;

        for (var method : IdcsRoleMapperProvider.class.getDeclaredMethods()) {
            if (!Modifier.isProtected(method.getModifiers())) {
                continue;
            }
            for (var parameterType : method.getParameterTypes()) {
                if (parameterType == RoleMapTracing.class) {
                    hasProtectedRoleMapTracingMethod = true;
                    break;
                }
            }
        }

        assertThat(hasProtectedRoleMapTracingMethod, is(false));
    }

    @Test
    void testAppTokenReadsHelidonJson() {
        WebClient webClient = Mockito.mock(WebClient.class);
        HttpClientRequest request = Mockito.mock(HttpClientRequest.class);
        HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
        String accessToken = signedToken();

        Mockito.when(webClient.post()).thenReturn(request);
        Mockito.when(request.uri(TOKEN_ENDPOINT_URI)).thenReturn(request);
        Mockito.when(request.header(HeaderValues.ACCEPT_JSON)).thenReturn(request);
        Mockito.when(request.submit(Mockito.any())).thenReturn(response);
        Mockito.when(response.status()).thenReturn(Status.OK_200);
        Mockito.when(response.as(JsonObject.class))
                .thenReturn(JsonObject.builder()
                                    .set("access_token", accessToken)
                                    .build());

        IdcsRoleMapperProviderBase.AppToken appToken =
                new IdcsRoleMapperProviderBase.AppToken(webClient, TOKEN_ENDPOINT_URI, Duration.ZERO);

        Optional<String> token = appToken.getToken(SecurityTracing.get().roleMapTracing("idcs"));

        assertThat(token.orElseThrow(), is(accessToken));
        Mockito.verify(request).header(HeaderValues.ACCEPT_JSON);

        ArgumentCaptor<Object> paramsCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(request).submit(paramsCaptor.capture());

        Parameters params = (Parameters) paramsCaptor.getValue();
        assertThat(params.first("grant_type").orElseThrow(), is("client_credentials"));
        assertThat(params.first("scope").orElseThrow(), is("urn:opc:idm:__myscopes__"));
    }

    private static IdcsRoleMapperProvider.Builder<?> roleMapperBuilder() {
        IdcsRoleMapperProvider.Builder<?> builder = IdcsRoleMapperProvider.builder();
        builder.oidcConfig(OidcConfig.builder()
                                   .oidcMetadataWellKnown(false)
                                   .clientId("client-id")
                                   .clientSecret("client-secret")
                                   .identityUri(IDENTITY_URI)
                                   .tokenEndpointUri(TOKEN_ENDPOINT_URI)
                                   .authorizationEndpointUri(AUTHORIZATION_ENDPOINT_URI)
                                   .build())
                .roleCache(EvictableCache.<String, List<Grant>>builder()
                                   .maxSize(2)
                                   .build());
        return builder;
    }

    private static String signedToken() {
        Instant issueTime = Instant.now();
        Jwt jwt = Jwt.builder()
                .algorithm("none")
                .issuer("unit-test")
                .issueTime(issueTime)
                .expirationTime(issueTime.plusSeconds(60))
                .build();

        return SignedJwt.sign(jwt, Jwk.NONE_JWK).tokenContent();
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

    private static final class TestProvider extends IdcsRoleMapperProvider {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        private TestProvider(Builder<?> builder) {
            super(builder);
        }

        @Override
        protected List<? extends Grant> getGrantsFromServer(Subject subject) {
            String id = subject.principal().id();
            return List.of(Role.create("counted_" + COUNTER.incrementAndGet()),
                           Role.create("fixed"),
                           Role.create(id));
        }

        @Override
        protected List<? extends Grant> addAdditionalGrants(Subject subject, List<Grant> idcsGrants) {
            return List.of(Role.create("additional_" + COUNTER.incrementAndGet()),
                           Role.create("additional-fixed"));
        }
    }

    private static final class CapturingRequestProvider extends IdcsRoleMapperProvider {
        private final String appToken;
        private HttpClientRequest request;
        private Object entity;
        private String subjectName;

        private CapturingRequestProvider(Builder<?> builder, String appToken) {
            super(builder);
            this.appToken = appToken;
        }

        @Override
        Optional<String> getAppToken(RoleMapTracing tracing) {
            return Optional.of(appToken);
        }

        @Override
        protected List<? extends Grant> processRoleRequest(HttpClientRequest request, Object entity, String subjectName) {
            this.request = request;
            this.entity = entity;
            this.subjectName = subjectName;
            return List.of();
        }

        private HttpClientRequest request() {
            return request;
        }

        private Object entity() {
            return entity;
        }

        private String subjectName() {
            return subjectName;
        }
    }
}
