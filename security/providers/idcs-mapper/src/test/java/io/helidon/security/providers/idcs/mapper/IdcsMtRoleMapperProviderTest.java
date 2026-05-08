/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.util.Optional;

import io.helidon.http.HeaderNames;
import io.helidon.json.JsonObject;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.RoleMapTracing;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webclient.api.HttpClientRequest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class IdcsMtRoleMapperProviderTest {
    private static final String ASSERTER_SCHEMA = "urn:ietf:params:scim:schemas:oracle:idcs:Asserter";
    private static final URI IDENTITY_URI = URI.create("https://idcs.example.com/identity/uri");
    private static final URI TOKEN_ENDPOINT_URI = URI.create("https://idcs.example.com/token/endpoint/uri");
    private static final URI AUTHORIZATION_ENDPOINT_URI =
            URI.create("https://idcs.example.com/authorization/endpoint/uri");
    private static final URI TENANT_ASSERT_URI = URI.create("https://tenant.example.com/admin/v1/Asserter");
    private static final URI TENANT_TOKEN_URI = URI.create("https://tenant.example.com/oauth2/v1/token");

    @Test
    void testGetGrantsFromServerSubmitsJsonObjectEntity() {
        CapturingMtProvider provider = new CapturingMtProvider(roleMapperBuilder(), "tenant-app-token");

        provider.getGrantsFromServer("tenant-1",
                                     "tenant-app",
                                     Subject.builder()
                                             .principal(Principal.create("test-user"))
                                             .build());

        HttpClientRequest request = provider.request();
        assertThat(request, notNullValue());
        assertThat(request.resolvedUri().host(), is("tenant.example.com"));
        assertThat(request.resolvedUri().path().path(), is("/admin/v1/Asserter"));
        assertThat(request.headers().contains(HeaderNames.CONTENT_TYPE), is(false));
        assertThat(request.headers().get(HeaderNames.AUTHORIZATION).get(), is("Bearer tenant-app-token"));
        assertThat(provider.subjectName(), is("test-user"));
        assertThat(provider.entity(), is(instanceOf(JsonObject.class)));

        JsonObject requestEntity = (JsonObject) provider.entity();
        assertThat(requestEntity.stringValue("mappingAttributeValue").orElseThrow(), is("test-user"));
        assertThat(requestEntity.stringValue("subjectType").orElseThrow(),
                   is(IdcsRoleMapperProviderBase.IDCS_SUBJECT_TYPE_USER));
        assertThat(requestEntity.stringValue("appName").orElseThrow(), is("tenant-app"));
        assertThat(requestEntity.booleanValue("includeMemberships").orElseThrow(), is(true));
        assertThat(requestEntity.arrayValue("schemas").orElseThrow().get(0).orElseThrow().asString().value(),
                   is(ASSERTER_SCHEMA));
    }

    private static IdcsMtRoleMapperProvider.Builder<?> roleMapperBuilder() {
        IdcsMtRoleMapperProvider.Builder<?> builder = IdcsMtRoleMapperProvider.builder();
        builder.oidcConfig(OidcConfig.builder()
                                   .oidcMetadataWellKnown(false)
                                   .clientId("client-id")
                                   .clientSecret("client-secret")
                                   .identityUri(IDENTITY_URI)
                                   .tokenEndpointUri(TOKEN_ENDPOINT_URI)
                                   .authorizationEndpointUri(AUTHORIZATION_ENDPOINT_URI)
                                   .build())
                .multitenantEndpoints(new TestEndpoints());
        return builder;
    }

    private static final class CapturingMtProvider extends IdcsMtRoleMapperProvider {
        private final String appToken;
        private HttpClientRequest request;
        private Object entity;
        private String subjectName;

        private CapturingMtProvider(Builder<?> builder, String appToken) {
            super(builder);
            this.appToken = appToken;
        }

        @Override
        protected Optional<String> getAppToken(String idcsTenantId, RoleMapTracing tracing) {
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

    private static final class TestEndpoints implements IdcsMtRoleMapperProvider.MultitenancyEndpoints {
        @Override
        public String idcsInfraTenantId() {
            return "infra-tenant";
        }

        @Override
        public URI assertEndpoint(String tenantId) {
            return TENANT_ASSERT_URI;
        }

        @Override
        public URI tokenEndpoint(String tenantId) {
            return TENANT_TOKEN_URI;
        }
    }
}
