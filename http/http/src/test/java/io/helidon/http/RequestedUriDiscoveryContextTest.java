/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.uri.UriQuery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestedUriDiscoveryContextTest {

    @Test
    void pathDoesNotProvideSchemeOrAuthority() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("client", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "https://victim.example/private",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "public.example:8080")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.scheme(), is("http"));
        assertThat(uriInfo.authority(), is("public.example:8080"));
    }

    @Test
    void absoluteTargetProvidesSchemeAndAuthorityDefaults() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("client", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "https",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "victim.example:9443")),
                         UriQuery.create("key=value"),
                         false);

        assertThat(uriInfo.toString(),
                   is("UriInfo{scheme=https,host=victim.example,port=9443,path=/private,"
                              + "query=?key=value,fragment=}"));
    }

    @Test
    void explicitRequestSchemeUsesEffectiveAuthority() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("client", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "http",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "victim.example")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toUri().toString(), is("http://victim.example:80/private"));
    }

    @ParameterizedTest
    @MethodSource("encodedStructuralAuthorities")
    void absoluteTargetPreservesEncodedStructuralHostData(String requestPath,
                                                          String requestScheme,
                                                          String requestAuthority,
                                                          String expectedAuthority,
                                                          String expectedUri) {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("client", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         requestPath,
                         requestScheme,
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, requestAuthority)),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.authority(), is(expectedAuthority));
        assertThat(uriInfo.toUri().toString(), is(expectedUri));
    }

    @Test
    void absoluteTargetEmptyPortUsesSchemeDefault() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("client", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "http",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "service_name")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toString(),
                   is("UriInfo{scheme=http,host=service_name,port=80,path=/private,query=,fragment=}"));
    }

    @Test
    void absoluteTargetSchemeIsFallbackForTrustedForwardedAuthority() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().addAllowed("trustedproxy").build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("trustedproxy", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "https",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "backend.example:9443")
                                                             .add(HeaderNames.FORWARDED,
                                                                  "for=client;host=external.example:8443")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toString(),
                   is("UriInfo{scheme=https,host=external.example,port=8443,path=/private,query=,fragment=}"));
    }

    @Test
    void absoluteTargetAuthorityIsFallbackForTrustedForwardedScheme() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().addAllowed("trustedproxy").build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("trustedproxy", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "http",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "backend.example:9080")
                                                             .add(HeaderNames.FORWARDED,
                                                                  "for=client;proto=https")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toString(),
                   is("UriInfo{scheme=https,host=backend.example,port=9080,path=/private,query=,fragment=}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1http", "not_a_scheme", "hé"})
    void invalidForwardedSchemeFallsBackToAbsoluteTarget(String forwardedScheme) {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().addAllowed("trustedproxy").build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("trustedproxy", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         "http",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "backend.example")
                                                             .add(HeaderNames.FORWARDED,
                                                                  "for=client;proto=" + forwardedScheme)),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toUri().toString(), is("http://backend.example:80/private"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1http", "not_a_scheme", "hé"})
    void invalidXForwardedSchemeIsBadRequest(String forwardedScheme) {
        var context = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                          .add(HeaderNames.HOST, "public.example:8080")
                                                          .add(HeaderNames.X_FORWARDED_FOR,
                                                               "client.example, proxy.example")
                                                          .add(HeaderNames.X_FORWARDED_PROTO, forwardedScheme));

        var exception = assertThrows(BadRequestException.class,
                                     () -> context.uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                                                           InetSocketAddress.createUnresolved("localhost", 8080),
                                                           "/private",
                                                           headers,
                                                           UriQuery.empty(),
                                                           false));

        assertThat(exception.getMessage(), is("Invalid requested URI discovery metadata"));
    }

    @Test
    void nonNumericXForwardedPortIsBadRequest() {
        var context = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                          .add(HeaderNames.HOST, "public.example:8080")
                                                          .add(HeaderNames.X_FORWARDED_FOR,
                                                               "client.example, proxy.example")
                                                          .add(HeaderNames.X_FORWARDED_PORT, "not-a-port"));

        var exception = assertThrows(BadRequestException.class,
                                     () -> context.uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                                                           InetSocketAddress.createUnresolved("localhost", 8080),
                                                           "/private",
                                                           headers,
                                                           UriQuery.empty(),
                                                           false));

        assertThat(exception.getMessage(), is("Invalid requested URI discovery metadata"));
    }

    @Test
    void incompleteQuotedForwardedValueIsBadRequest() {
        var context = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                          .add(HeaderNames.HOST, "public.example:8080")
                                                          .add(HeaderNames.FORWARDED,
                                                               "for=client.example;proto=https;ext=\"dangling\\"));

        var exception = assertThrows(BadRequestException.class,
                                     () -> context.uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                                                           InetSocketAddress.createUnresolved("localhost", 8080),
                                                           "/private",
                                                           headers,
                                                           UriQuery.empty(),
                                                           false));

        assertThat(exception.getMessage(), is("Invalid requested URI discovery metadata"));
    }

    @Test
    void nonNumericForwardedHostPortIsBadRequest() {
        var context = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                          .add(HeaderNames.HOST, "public.example:8080")
                                                          .add(HeaderNames.FORWARDED,
                                                               "for=client.example;"
                                                                       + "host=\"public.example:not-a-port\";"
                                                                       + "proto=https"));

        var exception = assertThrows(BadRequestException.class,
                                     () -> context.uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                                                           InetSocketAddress.createUnresolved("localhost", 8080),
                                                           "/private",
                                                           headers,
                                                           UriQuery.empty(),
                                                           false));

        assertThat(exception.getMessage(), is("Invalid requested URI discovery metadata"));
    }

    @Test
    void emptyForwardedHostPortUsesSchemeDefault() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "backend.example:8080")
                                                             .add(HeaderNames.FORWARDED,
                                                                  "for=client.example;"
                                                                          + "host=\"public.example:\";"
                                                                          + "proto=https")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toUri().toString(), is("https://public.example:443/private"));
    }

    @Test
    void forwardedRegisteredNameIsPreserved() {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                         InetSocketAddress.createUnresolved("localhost", 8080),
                         "/private",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.HOST, "backend.example:8080")
                                                             .add(HeaderNames.FORWARDED,
                                                                  "for=client.example;"
                                                                          + "host=\"service_name:9080\";"
                                                                          + "proto=https")),
                         UriQuery.empty(),
                         false);

        assertThat(uriInfo.toUri().toString(), is("https://service_name:9080/private"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"65536", "2147483648"})
    void outOfRangeForwardedHostPortIsBadRequest(String port) {
        var context = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(AllowList.builder().allowAll(true).build())
                .build();
        var headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                          .add(HeaderNames.HOST, "backend.example:8080")
                                                          .add(HeaderNames.FORWARDED,
                                                               "for=client.example;host=\"public.example:"
                                                                       + port
                                                                       + "\";proto=https"));

        var exception = assertThrows(BadRequestException.class,
                                     () -> context.uriInfo(InetSocketAddress.createUnresolved("proxy.example", 8080),
                                                           InetSocketAddress.createUnresolved("localhost", 8080),
                                                           "/private",
                                                           headers,
                                                           UriQuery.empty(),
                                                           false));

        assertThat(exception.getMessage(), is("Invalid requested URI discovery metadata"));
    }

    @ParameterizedTest
    @MethodSource("nullExplicitSchemeInputs")
    void defaultExplicitSchemeOverloadRejectsNullArguments(String expectedParameter,
                                                           SocketAddress remoteAddress,
                                                           SocketAddress localAddress,
                                                           String requestPath,
                                                           String requestScheme,
                                                           ServerRequestHeaders headers,
                                                           UriQuery query) {
        RequestedUriDiscoveryContext legacyContext = (_, _, _, _, _, _) -> {
            throw new AssertionError("Default method delegated with a null argument");
        };

        var exception = assertThrows(NullPointerException.class,
                                     () -> legacyContext.uriInfo(remoteAddress,
                                                                 localAddress,
                                                                 requestPath,
                                                                 requestScheme,
                                                                 headers,
                                                                 query,
                                                                 false));

        assertThat(exception.getMessage(), is(expectedParameter));
    }

    @ParameterizedTest
    @MethodSource("nullExplicitSchemeInputs")
    void builtInExplicitSchemeOverloadRejectsNullArguments(String expectedParameter,
                                                           SocketAddress remoteAddress,
                                                           SocketAddress localAddress,
                                                           String requestPath,
                                                           String requestScheme,
                                                           ServerRequestHeaders headers,
                                                           UriQuery query) {
        var exception = assertThrows(NullPointerException.class,
                                     () -> RequestedUriDiscoveryContext.builder()
                                             .build()
                                             .uriInfo(remoteAddress,
                                                      localAddress,
                                                      requestPath,
                                                      requestScheme,
                                                      headers,
                                                      query,
                                                      false));

        assertThat(exception.getMessage(), is(expectedParameter));
    }

    private static Stream<Arguments> params() throws UnknownHostException {
        return Stream.of(
                arguments(new InetSocketAddress(InetAddress.getLoopbackAddress(), 8088),
                          AllowList.builder()
                                  .addAllowed("localhost")
                                  .build(),
                          "UriInfo{scheme=https,host=helidon.io,port=443,path=/path,query=,fragment=}"),

                arguments(new InetSocketAddress(InetAddress.getLoopbackAddress(), 8088),
                          AllowList.builder()
                                  .addAllowed("127.0.0.1")
                                  .build(),
                          "UriInfo{scheme=https,host=helidon.io,port=443,path=/path,query=,fragment=}"),

                arguments(new InetSocketAddress(Inet6Address.getByAddress("localhost",
                                                                          Inet6Address.getByName("::1")
                                                                                  .getAddress()), 8088),
                          AllowList.builder()
                                  .addAllowed("localhost")
                                  .build(),
                          "UriInfo{scheme=https,host=helidon.io,port=443,path=/path,query=,fragment=}"),

                arguments(new InetSocketAddress(Inet6Address.getByAddress("localhost",
                                                                          Inet6Address.getByName("::1")
                                                                                  .getAddress()), 8088),
                          AllowList.builder()
                                  .addAllowed("0:0:0:0:0:0:0:1")
                                  .build(),
                          "UriInfo{scheme=https,host=helidon.io,port=443,path=/path,query=,fragment=}")
        );
    }

    @ParameterizedTest
    @MethodSource("params")
    void inetAddress(InetSocketAddress remotePeerAddress, AllowList allowList, String expected) throws UnknownHostException {
        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(allowList)
                .build()
                .uriInfo(remotePeerAddress,
                         remotePeerAddress,
                         "/path",
                         ServerRequestHeaders.create(WritableHeaders.create()
                                                             .add(HeaderNames.FORWARDED,
                                                                  "by=a.b.c;for=d.e.f;host=helidon.io;proto=https")),
                         UriQuery.create(URI.create("http://localhost:8088/path")),
                         true);

        assertEquals(expected, uriInfo.toString());
    }

    @ParameterizedTest
    @MethodSource("forwardedData")
    void testForwardedFor(String forwardForValues, String expected) {
        var headers = WritableHeaders.create()
                .add(HeaderNames.HOST, "serverinstance")
                .add(HeaderNames.X_FORWARDED_HOST, "serverpublic")
                .add(HeaderNames.X_FORWARDED_PORT, 8080)
                .add(HeaderNames.X_FORWARDED_PROTO, "http");
        if (forwardForValues != null) {
            headers.add(HeaderNames.X_FORWARDED_FOR, forwardForValues);
        }

        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .addDiscoveryType(RequestedUriDiscoveryContext.RequestedUriDiscoveryType.X_FORWARDED)
                .trustedProxies(AllowList.builder().addAllowed("trustedproxy").build())
                .build()
                .uriInfo(InetSocketAddress.createUnresolved("trustedproxy", 443),   // actual host which sent us the request
                         InetSocketAddress.createUnresolved("localhost", 443),       // receiving address
                         "/path",
                         ServerRequestHeaders.create(headers),
                         UriQuery.create(URI.create("http://localhost:8080/path")),
                         true);

        assertThat("Requested URI with " + (forwardForValues == null ? "no " : "") + "X_FORWARDED_FOR",
                   uriInfo.toString(),
                   is(expected));
    }

    static Stream<Arguments> forwardedData() {
        return Stream.of(
                // With no X_FORWARDED_FOR header, the server should ignore the other X_FORWARDED_* headers
                // and report the most recent proxy as the "client."
                Arguments.arguments(null,
                                    "UriInfo{scheme=https,host=serverinstance,port=443,path=/path,query=,fragment=}"),
                // With only the originating client in X_FORWARDED_FOR, there are no trusted proxy entries
                // validating the single X_FORWARDED_* values.
                Arguments.arguments("randomclient",
                                    "UriInfo{scheme=https,host=serverinstance,port=443,path=/path,query=,fragment=}"),
                // With X_FORWARDED_FOR present, the server should process the various X_FORWARDED_* headers.
                Arguments.arguments("randomclient,trustedproxy",
                                     "UriInfo{scheme=http,host=serverpublic,port=8080,path=/path,query=,fragment=}"));
    }

    private static Stream<Arguments> nullExplicitSchemeInputs() {
        SocketAddress remoteAddress = InetSocketAddress.createUnresolved("client", 8080);
        SocketAddress localAddress = InetSocketAddress.createUnresolved("localhost", 8080);
        String requestPath = "/private";
        String requestScheme = "http";
        ServerRequestHeaders headers = ServerRequestHeaders.create(WritableHeaders.create()
                                                                            .add(HeaderNames.HOST,
                                                                                 "public.example:8080"));
        UriQuery query = UriQuery.empty();
        return Stream.of(
                arguments("remoteAddress", null, localAddress, requestPath, requestScheme, headers, query),
                arguments("localAddress", remoteAddress, null, requestPath, requestScheme, headers, query),
                arguments("requestPath", remoteAddress, localAddress, null, requestScheme, headers, query),
                arguments("requestScheme", remoteAddress, localAddress, requestPath, null, headers, query),
                arguments("headers", remoteAddress, localAddress, requestPath, requestScheme, null, query),
                arguments("query", remoteAddress, localAddress, requestPath, requestScheme, headers, null));
    }

    private static Stream<Arguments> encodedStructuralAuthorities() {
        return Stream.of(
                arguments("/private",
                          "http",
                          "victim%40attacker.example",
                          "victim%40attacker.example:80",
                          "http://victim%40attacker.example:80/private"),
                arguments("/private",
                          "http",
                          "api.example.com%3A443",
                          "api.example.com%3A443:80",
                          "http://api.example.com%3A443:80/private"));
    }

}
