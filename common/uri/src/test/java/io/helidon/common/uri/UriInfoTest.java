/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.uri;

import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class UriInfoTest {
    @Test
    void testDefaults() {
        UriInfo uriInfo = UriInfo.create();

        assertAll(
                () -> assertThat(uriInfo.scheme(), is("http")),
                () -> assertThat(uriInfo.host(), is("localhost")),
                () -> assertThat(uriInfo.port(), is(80)),
                () -> assertThat(uriInfo.authority(), is("localhost:80")),
                () -> assertThat(uriInfo.path(), is(UriPath.root())),
                () -> assertThat(uriInfo.query(), is(UriQuery.empty())),
                () -> assertThat(uriInfo.fragment(), is(UriFragment.empty())),
                () -> assertThat(uriInfo.toUri(), is(URI.create("http://localhost:80/")))
        );
    }
    @Test
    void testFullyCustomized() {
        UriInfo uriInfo = UriInfo.builder()
                .scheme("https")
                .host("helidon.io")
                .port(447)
                .path("/docs")
                .query(UriQueryWriteable.create()
                               .add("first", "firstValue")
                               .add("second", "secondValue"))
                .fragment(UriFragment.createFromDecoded("myNiceFragment"))
                .build();

        URI uri = URI.create("https://helidon.io:447/docs?first=firstValue&second=secondValue#myNiceFragment");
        assertAll(
                () -> assertThat(uriInfo.scheme(), is("https")),
                () -> assertThat(uriInfo.host(), is("helidon.io")),
                () -> assertThat(uriInfo.port(), is(447)),
                () -> assertThat(uriInfo.authority(), is("helidon.io:447")),
                () -> assertThat(uriInfo.path(), is(UriPath.create("/docs"))),
                () -> assertThat(uriInfo.query(), is(UriQueryWriteable.create()
                                                             .add("first", "firstValue")
                                                             .add("second", "secondValue"))),
                () -> assertThat(uriInfo.fragment(), is(UriFragment.create("myNiceFragment"))),
                () -> assertThat(uriInfo.toUri(), is(uri))
        );
    }
    @ParameterizedTest
    @MethodSource("authorities")
    void testAuthority(AuthorityData data) {
        UriInfo uriInfo = UriInfo.builder()
                .port(4444)
                .authority(data.authority())
                .build();

        assertAll(
                () -> assertThat(uriInfo.authority(), is(data.expectedAuthority())),
                () -> assertThat(uriInfo.host(), is(data.expectedHost())),
                () -> assertThat(uriInfo.port(), is(data.expectedPort()))
        );

    }

    private static Stream<AuthorityData> authorities() {
        return Stream.of(
                new AuthorityData("localhost", "localhost:4444", "localhost", 4444),
                new AuthorityData("localhost:80", "localhost:80", "localhost", 80),
                new AuthorityData("192.168.1.1:4744", "192.168.1.1:4744", "192.168.1.1", 4744),
                new AuthorityData("[2356:0102:3238:3876:1122:2232:4321]",
                                  "[2356:0102:3238:3876:1122:2232:4321]:4444",
                                  "[2356:0102:3238:3876:1122:2232:4321]",
                                  4444),
                new AuthorityData("[2356:0102:3238:3876:1122:2232:4321]:8080",
                                  "[2356:0102:3238:3876:1122:2232:4321]:8080",
                                  "[2356:0102:3238:3876:1122:2232:4321]",
                                  8080)
        );
    }

    private record AuthorityData(String authority, String expectedAuthority, String expectedHost, int expectedPort) {
    }
}
