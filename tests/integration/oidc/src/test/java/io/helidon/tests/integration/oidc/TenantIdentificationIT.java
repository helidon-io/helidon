/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.oidc;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@HelidonTest(resetPerTest = true)
@AddBean(TestResource.class)
@AddConfig(key = "security.providers.1.oidc.oidc-metadata-well-known", value = "false")
class TenantIdentificationIT {

    @Test
    @AddConfig(key = "security.providers.1.oidc.tenant-id-style", value = "host-header")
    void testHostHeaderTenantId(WebTarget webTarget) {
        try (Response response = webTarget.property(ClientProperties.FOLLOW_REDIRECTS, false).path("/test").request().get()) {
            String redirectUri = queryParamValue((String) response.getHeaders().getFirst(HttpHeaders.LOCATION), "redirect_uri");
            String tenantName = queryParamValue(redirectUri, OidcConfig.DEFAULT_TENANT_PARAM_NAME);
            assertThat(tenantName, is("localhost"));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.tenant-id-style", value = "token-handler")
    @AddConfig(key = "security.providers.1.oidc.tenant-id-handler.header", value = "test-tenant-name")
    void testTokenHandlerTenantId(WebTarget webTarget) {
        String expectedTenantName = "test-tenant";
        try (Response response = webTarget.property(ClientProperties.FOLLOW_REDIRECTS, false)
                .path("/test")
                .request()
                .header("test-tenant-name", expectedTenantName)
                .get()) {
            String redirectUri = queryParamValue((String) response.getHeaders().getFirst(HttpHeaders.LOCATION), "redirect_uri");
            String tenantName = queryParamValue(redirectUri, OidcConfig.DEFAULT_TENANT_PARAM_NAME);
            assertThat(tenantName, is(expectedTenantName));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.tenant-id-style", value = "domain")
    @AddConfig(key = "security.providers.1.oidc.tenant-id-domain-level", value = "1")
    void testDomainTenantId(WebTarget webTarget) {
        try (Response response = webTarget.property(ClientProperties.FOLLOW_REDIRECTS, false).path("/test").request().get()) {
            String redirectUri = queryParamValue((String) response.getHeaders().getFirst(HttpHeaders.LOCATION), "redirect_uri");
            String tenantName = queryParamValue(redirectUri, OidcConfig.DEFAULT_TENANT_PARAM_NAME);
            assertThat(tenantName, is("localhost"));
        }
    }

    @Test
    @AddConfig(key = "security.providers.1.oidc.tenant-id-style", value = "none")
    void testNoneTenantId(WebTarget webTarget) {
        try (Response response = webTarget.property(ClientProperties.FOLLOW_REDIRECTS, false).path("/test").request().get()) {
            String redirectUri = queryParamValue((String) response.getHeaders().getFirst(HttpHeaders.LOCATION), "redirect_uri");
            String tenantName = queryParamValue(redirectUri, OidcConfig.DEFAULT_TENANT_PARAM_NAME);
            assertThat(tenantName, is(TenantConfigFinder.DEFAULT_TENANT_ID));
        }
    }

    private String queryParamValue(String uriString, String queryName) {
        URI uri = URI.create(uriString);
        Map<String, List<String>> queryPairs = new LinkedHashMap<>();
        String[] pairs = uri.getRawQuery().split("&");
        for (String pair : pairs) {
            String[] keyValues = pair.split("=");
            String key = decode(keyValues[0]);
            List<String> values = queryPairs.computeIfAbsent(key, s -> new LinkedList<>());
            if (keyValues.length != 1) {
                values.add(decode(keyValues[1]));
            }
        }
        List<String> queryValues = queryPairs.get(queryName);
        assertThat(queryValues, notNullValue());
        assertThat(queryValues.size(), greaterThanOrEqualTo(1));
        return queryValues.get(0);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

}
