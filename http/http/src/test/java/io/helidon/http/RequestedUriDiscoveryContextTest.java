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
}
