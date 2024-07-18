/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static io.helidon.common.uri.UriEncoding.decodeUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class UriEncodingTest {

    @Test
    void testSpaceDecoding() {
        assertThat(decodeUri("%20hello%20world%20"), is(" hello world "));
        assertThat(decodeUri("[%20]hello[%20]world[%20]"), is("[%20]hello[%20]world[%20]"));
        assertThat(decodeUri("+hello+world+"), is(" hello world "));
        assertThat(decodeUri("[+]hello[+]world[+]"), is("[+]hello[+]world[+]"));
    }

    @Test
    void testIPv6Literal() {
        assertThat(decodeUri("http://[fe80::1%lo0]:8080"), is("http://[fe80::1%lo0]:8080"));
    }
}
