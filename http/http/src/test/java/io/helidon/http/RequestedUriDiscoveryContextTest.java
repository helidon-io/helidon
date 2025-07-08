/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.net.URI;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.uri.UriQuery;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class RequestedUriDiscoveryContextTest {

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
    @MethodSource("params")
    @Deprecated(forRemoval = true, since = "4.2.1")
    void stringAddress(InetSocketAddress remotePeerAddress, AllowList allowList, String expected) throws UnknownHostException {
        if (!allowList.prototype().allowed().getFirst().equals("localhost")) {
            Assumptions.abort("IP address filtering is not supported by deprecated api.");
        }

        var uriInfo = RequestedUriDiscoveryContext.builder()
                .enabled(true)
                .trustedProxies(allowList)
                .build()
                .uriInfo(remotePeerAddress.toString(),
                         remotePeerAddress.toString(),
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
    void testForwardedFor(String forwardForValues, String expected) throws UnknownHostException {
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
                .uriInfo("trustedproxy/1.2.3.4:443",   // actual host which sent us the request
                         "localhost/127.0.0.1:443",                 // receiving address
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
                // With X_FORWARDED_FOR present, the server should process the various X_FORWARDED_* headers.
                Arguments.arguments("randomclient,trustedproxy",
                                     "UriInfo{scheme=http,host=serverpublic,port=8080,path=/path,query=,fragment=}"));
    }

}
