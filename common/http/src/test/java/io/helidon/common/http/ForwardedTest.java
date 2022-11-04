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
package io.helidon.common.http;

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.OptionalMatcher.empty;
import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class ForwardedTest {
    @Test
    void testForOnly() {
        String header = "for=\"_mdn\"";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), is(empty()));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), value(is("_mdn")));
    }

    @Test
    void testForCaseInsensitive() {
        String header = "For=\"[2001:db8:cafe::17]:4711\"";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), is(empty()));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), value(is("[2001:db8:cafe::17]:4711")));
    }

    @Test
    void testForProtoAndBy() {
        String header = "for=192.0.2.60;proto=http;by=203.0.113.43";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), value(is("203.0.113.43")));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), value(is("http")));
        assertThat(forwarded.forClient(), value(is("192.0.2.60")));
    }

    @Test
    void testAll() {
        String header = "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10";
        Forwarded forwarded = Forwarded.create(header);

        assertThat(forwarded.by(), value(is("203.0.113.43")));
        assertThat(forwarded.host(), value(is("10.10.10.10")));
        assertThat(forwarded.proto(), value(is("http")));
        assertThat(forwarded.forClient(), value(is("192.0.2.60")));
    }

    @Test
    void testMultiValuesCommaSeparated() {
        HashHeaders headers = HashHeaders.create();
        headers.add(Http.Header.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10,by=\"192.0.2.60\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(2));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), value(is("203.0.113.43")));
        assertThat(forwarded.host(), value(is("10.10.10.10")));
        assertThat(forwarded.proto(), value(is("http")));
        assertThat(forwarded.forClient(), value(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), value(is("192.0.2.60")));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), is(empty()));
    }

    @Test
    void testMultiValues() {
        HashHeaders headers = HashHeaders.create();
        headers.add(Http.Header.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10",
                    "by=\"192.0.2.60\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(2));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), value(is("203.0.113.43")));
        assertThat(forwarded.host(), value(is("10.10.10.10")));
        assertThat(forwarded.proto(), value(is("http")));
        assertThat(forwarded.forClient(), value(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), value(is("192.0.2.60")));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), is(empty()));
    }

    @Test
    void testMultiValuesAndCommaSeparated() {
        HashHeaders headers = HashHeaders.create();
        headers.add(Http.Header.FORWARDED, "for=192.0.2.60;proto=http;by=203.0.113.43;Host=10.10.10.10",
                    "by=\"192.0.2.60\",for=\"14.22.11.22\"");
        List<Forwarded> forwardedList = Forwarded.create(headers);

        assertThat(forwardedList, hasSize(3));
        Forwarded forwarded = forwardedList.get(0);

        assertThat(forwarded.by(), value(is("203.0.113.43")));
        assertThat(forwarded.host(), value(is("10.10.10.10")));
        assertThat(forwarded.proto(), value(is("http")));
        assertThat(forwarded.forClient(), value(is("192.0.2.60")));

        forwarded = forwardedList.get(1);
        assertThat(forwarded.by(), value(is("192.0.2.60")));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), is(empty()));

        forwarded = forwardedList.get(2);
        assertThat(forwarded.by(), is(empty()));
        assertThat(forwarded.host(), is(empty()));
        assertThat(forwarded.proto(), is(empty()));
        assertThat(forwarded.forClient(), value(is("14.22.11.22")));
    }
}
