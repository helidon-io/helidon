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

package io.helidon.security.providers.oidc;

import java.net.URI;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.uri.UriInfo;
import io.helidon.http.BadRequestException;
import io.helidon.http.HeaderNames;
import io.helidon.http.UnauthorizedException;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webserver.http.ServerRequest;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OidcUtilTest {

    @Test
    void testLocalRedirectUriValidation() {
        assertThat(OidcUtil.localRedirectUri("/test").orElseThrow(), is("/test"));
        assertThat(OidcUtil.localRedirectUri("/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest").orElseThrow(),
                   is("/raw%2Fpath?return=https%3A%2F%2Fexample.com%2Ftest"));
        assertThat(OidcUtil.localRedirectUri("https://example.com/test").isEmpty(), is(true));
        assertThat(OidcUtil.localRedirectUri("//example.com/test").isEmpty(), is(true));
        assertThat(OidcUtil.localRedirectUri("/\\example.com/test").isEmpty(), is(true));
        assertThat(OidcUtil.localRedirectUri("/%2f%2fexample.com/test").isEmpty(), is(true));
        assertThat(OidcUtil.localRedirectUri("/%5cexample.com/test").isEmpty(), is(true));
        assertThat(OidcUtil.localRedirectUri("/test\nset-cookie: test=value").isEmpty(), is(true));
    }

    @Test
    void configuredFrontendPathBypassesDiscoveryAndAllowList() {
        OidcConfig config = builder().frontendUri("https://frontend.example/application").build();

        assertThat(OidcUtil.frontendUri(config, config, () -> {
            throw new IllegalArgumentException("Unusable requested URI");
        }), is("https://frontend.example/application"));
    }

    @Test
    void missingAllowListDeniesDynamicOrigin() {
        OidcConfig config = builder().build();
        UnauthorizedException failure = assertThrows(UnauthorizedException.class,
                () -> OidcUtil.frontendUri(config, config, () -> URI.create("https://frontend.example/path")));
        assertThat(failure.status().code(), is(401));
        assertThat(failure.headers().first(HeaderNames.WWW_AUTHENTICATE).orElseThrow(),
                   is("Bearer realm=\"helidon\""));
    }

    @Test
    void discoveryFailureIncludesEscapedTenantRealmChallenge() {
        OidcConfig config = builder().build();
        TenantConfig tenantConfig = mock(TenantConfig.class);
        when(tenantConfig.realm()).thenReturn("front\"end\\realm");
        UnauthorizedException failure = assertThrows(UnauthorizedException.class,
                () -> OidcUtil.frontendUri(config, tenantConfig, () -> {
                    throw new BadRequestException("Malformed requested URI");
                }));

        assertThat(failure.headers().first(HeaderNames.WWW_AUTHENTICATE).orElseThrow(),
                   is("Bearer realm=\"front\\\"end\\\\realm\""));
    }

    @Test
    void expandedIpv6IsAcceptedWithAllowAll() {
        OidcConfig config = builder().frontendUriAllowList(AllowList.builder().allowAll(true).build()).build();

        assertThat(OidcUtil.frontendUri(config, config, () -> URI.create("https://[0:0:0:0:0:0:0:1]/path")),
                   is("https://[::1]"));
    }

    @Test
    void expandedIpv6CannotBypassCanonicalDenyRule() {
        OidcConfig config = builder().frontendUriAllowList(AllowList.builder()
                .allowAll(true).addDenied("https://[::1]").build()).build();

        assertThrows(UnauthorizedException.class,
                     () -> OidcUtil.frontendUri(config, config, () -> URI.create("https://[0:0:0:0:0:0:0:1]/path")));
    }

    @Test
    void differentOidcConfigurationsHaveIndependentPolicies() {
        OidcConfig allowed = builder().frontendUriAllowList(AllowList.builder()
                                                                  .addAllowed("https://frontend.example").build()).build();
        OidcConfig denied = builder().frontendUriAllowList(AllowList.builder()
                                                                 .addAllowed("https://other.example").build()).build();

        assertThat(OidcUtil.frontendUri(allowed, allowed, () -> URI.create("https://frontend.example/path")),
                   is("https://frontend.example"));
        assertThrows(UnauthorizedException.class,
                     () -> OidcUtil.frontendUri(denied, denied, () -> URI.create("https://frontend.example/path")));
    }

    @Test
    void denyRuleWinsOverAllowRule() {
        OidcConfig config = builder().frontendUriAllowList(AllowList.builder()
                                                                 .allowAll(true)
                                                                 .addDenied("https://frontend.example").build()).build();
        assertThrows(UnauthorizedException.class,
                     () -> OidcUtil.frontendUri(config, config, () -> URI.create("https://frontend.example/path")));
    }

    @Test
    void invalidDiscoveryIsUnauthorized() {
        OidcConfig config = builder().build();
        assertThrows(UnauthorizedException.class, () -> OidcUtil.frontendUri(config, config, () -> null));
        assertThrows(UnauthorizedException.class, () -> OidcUtil.frontendUri(config, config, () -> {
            throw new IllegalArgumentException("Malformed URI");
        }));
        assertThrows(UnauthorizedException.class, () -> OidcUtil.frontendUri(config, config, () -> {
            throw new BadRequestException("Malformed discovered origin");
        }));
    }

    @Test
    void securityEnvironmentTakesPrecedenceOverServerRequest() {
        ServerRequest request = mock(ServerRequest.class);
        UriInfo contextualUri = UriInfo.builder().scheme("http").host("attacker.example").build();
        when(request.requestedUri()).thenReturn(contextualUri);
        URI discovered = URI.create("https://frontend.example:8443/path");
        SecurityEnvironment environment = SecurityEnvironment.builder()
                .targetUri(discovered)
                .build();
        Context context = Context.builder().id("oidc-requested-uri").build();
        context.supply(ServerRequest.class, () -> request);

        Contexts.runInContext(context,
                              () -> assertThat(OidcUtil.requestedUri(environment), is(discovered)));
    }

    @Test
    void securityEnvironmentRetainsDiscoveredUriWithoutServerRequest() {
        URI discovered = URI.create("https://frontend.example:8443/path");
        SecurityEnvironment environment = SecurityEnvironment.builder().targetUri(discovered).build();

        assertThat(OidcUtil.requestedUri(environment), is(discovered));
    }

    @Test
    void missingEnvironmentUriDoesNotFallBackToServerRequest() {
        OidcConfig config = builder().frontendUriAllowList(AllowList.builder().allowAll(true).build()).build();
        ServerRequest request = mock(ServerRequest.class);
        when(request.requestedUri()).thenReturn(UriInfo.builder().scheme("https").host("frontend.example").build());
        SecurityEnvironment environment = SecurityEnvironment.create();
        Context context = Context.builder().id("oidc-missing-environment-uri").build();
        context.supply(ServerRequest.class, () -> request);

        Contexts.runInContext(context,
                              () -> assertThrows(UnauthorizedException.class,
                                                 () -> OidcUtil.frontendUri(config,
                                                                            config,
                                                                            () -> OidcUtil.requestedUri(environment))));
    }

    @TestFactory
    Stream<DynamicTest> invalidRequestedOriginIsRejectedEvenWithAllowAll() {
        return Stream.of("https://user@frontend.example/path",
                         "https://user%3Aname@frontend.example/path",
                         "https://frontend.example:0/path",
                         "https://frontend.example:65536/path",
                         "https://frontend.example:abc/path",
                         "https://[bad/path",
                         "https://[::ffff:017.0.0.1]/path",
                         "https://[fe80::1%eth0]/path",
                         "https://127.1/path",
                         "https://0177.0.0.1/path",
                         "https://0x7f000001/path",
                         "ftp://frontend.example/path",
                         "/relative")
                .map(uri -> dynamicTest(uri, () -> {
                    OidcConfig config = builder().forceHttpsRedirects(true)
                            .frontendUriAllowList(AllowList.builder().allowAll(true).build()).build();

                    assertThrows(UnauthorizedException.class, () -> OidcUtil.frontendUri(config, config, () -> URI.create(uri)));
                }));
    }

    @TestFactory
    Stream<DynamicTest> requestedOriginIsNormalizedBeforeMatching() {
        return Stream.of(new OriginCase("HTTPS://FRONTEND.EXAMPLE:443/path?q=x#fragment", false, "https://frontend.example"),
                         new OriginCase("http://frontend.example/path", false, "http://frontend.example"),
                         new OriginCase("http://frontend.example:80/path", false, "http://frontend.example"),
                         new OriginCase("https://frontend.example:8443/path", false, "https://frontend.example:8443"),
                         new OriginCase("https://[::1]/path", false, "https://[::1]"),
                         new OriginCase("https://[0:0:0:0:0:0:0:1]/path", false, "https://[::1]"),
                         new OriginCase("https://[2001:0DB8:0:0:0:0:0:1]/path", false, "https://[2001:db8::1]"),
                         new OriginCase("https://[::ffff:192.0.2.1]/path", false, "https://[::ffff:c000:201]"),
                         new OriginCase("https://[1111:2222:3333:4444:5555:6666:192.168.100.200]/path",
                                        false, "https://[1111:2222:3333:4444:5555:6666:c0a8:64c8]"),
                         new OriginCase("http://frontend.example/path", true, "https://frontend.example"),
                         new OriginCase("http://frontend.example:80/path", true, "https://frontend.example"),
                         new OriginCase("http://frontend.example:8080/path", true, "https://frontend.example:8080"),
                         new OriginCase("http://frontend.example:443/path", true, "https://frontend.example"))
                .map(test -> dynamicTest(test.uri(), () -> {
                    OidcConfig config = builder().forceHttpsRedirects(test.forceHttps())
                            .frontendUriAllowList(AllowList.builder().addAllowed(test.expected()).build()).build();

                    assertThat(OidcUtil.frontendUri(config, config, () -> URI.create(test.uri())), is(test.expected()));
                }));
    }

    private static OidcConfig.Builder builder() {
        return OidcConfig.builder()
                .clientId("test")
                .clientSecret("secret")
                .identityUri(URI.create("https://identity.example"))
                .oidcMetadataWellKnown(false);
    }

    private record OriginCase(String uri, boolean forceHttps, String expected) {
    }
}
