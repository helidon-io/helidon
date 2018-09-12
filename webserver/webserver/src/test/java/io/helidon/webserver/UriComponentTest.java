/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Optional;

import io.helidon.common.http.Parameters;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

/**
 * The UriComponentTest.
 */
public class UriComponentTest {

    @Test
    public void noDecode() throws Exception {
        Parameters parameters = UriComponent.decodeQuery("a=" + URLEncoder.encode("1&b=2", US_ASCII.name()), false);

        assertThat(parameters.first("a").get(), is(URLEncoder.encode("1&b=2", US_ASCII.name())));
    }

    @Test
    public void yesDecode() throws Exception {
        Parameters parameters = UriComponent.decodeQuery("a=" + URLEncoder.encode("1&b=2", US_ASCII.name()), true);

        assertThat(parameters.first("a").get(), is("1&b=2"));
    }

    @Test
    public void testNonExistingParam() throws Exception {
        Parameters parameters = UriComponent.decodeQuery("a=b", true);

        assertThat(parameters.first("c"), is(Optional.empty()));
    }

    @Test
    public void sanityParse() throws Exception {
        Parameters parameters = UriComponent.decodeQuery(URI.create("http://foo/bar?a=b&c=d&a=e").getRawQuery(), true);

        assertThat(parameters.all("a"), hasItems(is("b"), is("e")));
        assertThat(parameters.all("c"), hasItems(is("d")));
        assertThat(parameters.all("z"), hasSize(0));
    }
}
