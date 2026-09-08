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

package io.helidon.webserver.security;

import java.util.List;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.testing.http.junit5.SocketHttpClient;
import io.helidon.http.DirectHandler;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.context.ContextFeature;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class AbsoluteFormRequestTargetTest {
    private final SocketHttpClient client;

    AbsoluteFormRequestTargetTest(SocketHttpClient client) {
        this.client = client;
    }

    @SetUpServer
    static void setup(WebServerConfig.Builder server) {
        var requestedUriDiscovery = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.FORWARDED)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        server.featuresDiscoverServices(false)
                .requestedUriDiscoveryContext(requestedUriDiscovery)
                .directHandlers(DirectHandlers.builder()
                                        .addHandler(DirectHandler.EventType.BAD_REQUEST,
                                                    (request, eventType, status, responseHeaders, message) ->
                                                            DirectHandler.TransportResponse.builder()
                                                                    .status(status)
                                                                    .entity(request.method()
                                                                                    + '|' + request.path()
                                                                                    + '|' + request.headers()
                                                                                            .first(HeaderNames.HOST)
                                                                                            .orElse(""))
                                                                    .build())
                                        .build())
                .addFeature(ContextFeature.create())
                .addFeature(SecurityFeature.builder()
                                    .security(Security.builder().build())
                                    .build())
                .routing(routing -> routing.any((req, res) -> {
                    var securityContext = req.context().get(SecurityContext.class).orElseThrow();
                    res.send(req.prologue().uriPath().path()
                                     + '|' + req.requestedUri().toUri()
                                     + '|' + securityContext.env().targetUri()
                                     + '|' + req.authority()
                                     + '|' + securityContext.env().headers().get("host").getFirst());
                }));
    }

    @Test
    void rejectsSecureAbsoluteFormOverPlaintext() {
        String response = sendAndReceive("https://victim.example:9443/private?key=value", List.of());

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void absoluteFormOverridesConflictingHost() {
        String response = sendAndReceive("http://victim.example:9080/private?key=value", List.of());

        assertThat(response,
                   containsString("/private|http://victim.example:9080/private?key=value"
                                          + "|http://victim.example:9080/private?key=value"
                                          + "|victim.example:9080"
                                          + "|victim.example:9080"));
    }

    @Test
    void absoluteFormPreservesZeroPort() {
        String response = sendAndReceive("http://victim.example:0/private", List.of());

        assertThat(response,
                   containsString("/private|http://victim.example:0/private"
                                          + "|http://victim.example:0/private"
                                          + "|victim.example:0"
                                          + "|victim.example:0"));
    }

    @Test
    void rejectsAbsoluteTargetWithUserInfo() {
        assertUserInfoRejected("http://trusted.example@victim.example/private");
    }

    @Test
    void rejectsRegistryAuthorityWithUserInfo() {
        assertUserInfoRejected("http://trusted.example@service_name/private");
    }

    @Test
    void rejectsAbsoluteTargetWithMultipleUserInfoDelimiters() {
        assertUserInfoRejected("http://trusted.example@@victim.example/private");
    }

    @Test
    void acceptsRegisteredNameAuthority() {
        String response = sendAndReceive("http://service_name:9080/private", List.of());

        assertThat(response,
                   containsString("/private|http://service_name:9080/private"
                                          + "|http://service_name:9080/private"
                                          + "|service_name:9080"
                                          + "|service_name:9080"));
    }

    @Test
    void decodesAbsoluteFormAuthorityForRequestedUri() {
        String response = sendAndReceive("http://%76ictim.example/private", List.of());

        assertThat(response,
                   containsString("/private|http://victim.example:80/private"
                                          + "|http://victim.example:80/private"
                                          + "|victim.example"
                                          + "|victim.example"));
    }

    @Test
    void preservesEncodedUserInfoDelimiterAsHostData() {
        String response = sendAndReceive("http://victim%40attacker.example/private", List.of());

        assertThat(response,
                   containsString("/private|http://victim%40attacker.example:80/private"
                                          + "|http://victim%40attacker.example:80/private"
                                          + "|victim%40attacker.example"
                                          + "|victim%40attacker.example"));
    }

    @Test
    void preservesEncodedPortDelimiterAsHostData() {
        String response = sendAndReceive("http://api.example.com%3A443/private", List.of());

        assertThat(response,
                   containsString("/private|http://api.example.com%3A443:80/private"
                                          + "|http://api.example.com%3A443:80/private"
                                          + "|api.example.com%3A443"
                                          + "|api.example.com%3A443"));
    }

    @Test
    void acceptsEmptyAuthorityPortAsDefault() {
        String response = sendAndReceive("http://service_name:/private", List.of());

        assertThat(response,
                   containsString("/private|http://service_name:80/private"
                                          + "|http://service_name:80/private"
                                          + "|service_name"
                                          + "|service_name"));
    }

    @Test
    void encodedOriginFormPathDoesNotOverrideAuthority() {
        String response = sendAndReceive("/https%3A%2F%2Fvictim.example/private", List.of());

        assertThat(response,
                   containsString("|http://public.example:8080/https:/victim.example/private"
                                          + "|http://public.example:8080/https:/victim.example/private"
                                          + "|public.example:8080"
                                          + "|public.example:8080"));
    }

    @Test
    void trustedForwardedMetadataOverridesAbsoluteForm() {
        String response = sendAndReceive("https://backend.example:8443/private",
                                         List.of("Forwarded: for=client.example;"
                                                         + "host=external.example:8443;"
                                                         + "proto=https"));

        assertThat(response,
                   containsString("/private|https://external.example:8443/private"
                                          + "|https://external.example:8443/private"
                                          + "|backend.example:8443"
                                          + "|backend.example:8443"));
    }

    @Test
    void acceptsQuotedPairInTrustedForwardedHost() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("Forwarded: for=client.example;"
                                                         + "host=\"public.example\\:443\";"
                                                         + "proto=https"));

        assertThat(response,
                   containsString("/private|https://public.example:443/private"
                                          + "|https://public.example:443/private"
                                          + "|backend.example"
                                          + "|backend.example"));
    }

    @Test
    void trustedForwardedSchemeUsesAbsoluteFormAuthority() {
        String response = sendAndReceive("https://backend.example:8443/private",
                                         List.of("Forwarded: for=client.example;proto=https"));

        assertThat(response,
                   containsString("/private|https://backend.example:8443/private"
                                          + "|https://backend.example:8443/private"
                                          + "|backend.example:8443"
                                          + "|backend.example:8443"));
    }

    @Test
    void acceptsQuotedCommaInTrustedForwardedExtension() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("Forwarded: for=client.example;proto=https;ext=\"a,b\""));

        assertThat(response, startsWith("HTTP/1.1 200 "));
    }

    @Test
    void rejectsIncompleteQuotedForwardedExtensionAsBadRequest() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("Forwarded: for=untrusted;host=evil.example;proto=https;"
                                                         + "ext=\"x\\\",for=client.example;by=proxy.example"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsIncompleteQuotedForwardedExtensionForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("Forwarded: for=untrusted;host=evil.example;proto=https;"
                                                         + "ext=\"x\\\",for=client.example;by=proxy.example"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsNonNumericTrustedForwardedHostPortForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("Forwarded: for=client.example;"
                                                         + "host=\"public.example:not-a-port\";"
                                                         + "proto=https"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void acceptsEmptyTrustedForwardedHostPortForOriginForm() {
        String response = sendAndReceive("/private",
                                         List.of("Forwarded: for=client.example;"
                                                         + "host=\"public.example:\";"
                                                         + "proto=https"));

        assertThat(response, startsWith("HTTP/1.1 200 "));
    }

    @Test
    void rejectsOutOfRangeTrustedForwardedHostPortForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("Forwarded: for=client.example;"
                                                         + "host=\"public.example:65536\";"
                                                         + "proto=https"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsEmptyTrustedForwardedSchemeAsBadRequest() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("Forwarded: for=client.example;proto="));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsMalformedTrustedForwardedMetadataAsBadRequest() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("Forwarded: invalid"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsEmptyTrustedXForwardedSchemeAsBadRequest() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto:"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsEmptyTrustedXForwardedSchemeForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto:"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsInvalidTrustedXForwardedSchemeForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto: not_a_scheme"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsNonNumericTrustedForwardedPortAsBadRequest() {
        String response = sendAndReceive("https://backend.example/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto: https",
                                                 "X-Forwarded-Port: not-a-port"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsNonNumericTrustedXForwardedPortForOriginFormAsBadRequest() {
        String response = sendAndReceive("/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto: https",
                                                 "X-Forwarded-Port: not-a-port"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void malformedTrustedForwardingPreservesParsedRequestForBadRequestHandler() {
        String response = sendAndReceive("/private",
                                         List.of("X-Forwarded-For: client.example, proxy.example",
                                                 "X-Forwarded-Proto: https",
                                                 "X-Forwarded-Port: not-a-port"));

        assertThat(response, containsString("GET|/private|public.example:8080"));
    }

    @Test
    void rejectsSecureAbsoluteFormWhenTrustedMetadataOmitsScheme() {
        String response = sendAndReceive("https://victim.example:9443/private",
                                         List.of("Forwarded: for=client.example;host=external.example:8443"));

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsMalformedAbsoluteAuthorityPort() {
        String response = sendAndReceive("http://victim.example:not-a-port/private", List.of());

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsOutOfRangeAbsoluteAuthorityPort() {
        String response = sendAndReceive("http://victim.example:65536/private", List.of());

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    @Test
    void rejectsHttpSchemeWithoutAuthority() {
        String response = sendAndReceive("http:/private", List.of());

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }

    private String sendAndReceive(String target, List<String> headers) {
        client.request(Method.GET.text(), target, "HTTP/1.1", "public.example:8080", headers, null);
        return client.receive();
    }

    private void assertUserInfoRejected(String requestTarget) {
        String response = sendAndReceive(requestTarget, List.of());

        assertThat(response, startsWith("HTTP/1.1 400 "));
    }
}
