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

package io.helidon.http.encoding;

import java.util.List;

import org.junit.jupiter.api.Test;
import io.helidon.http.encoding.ContentEncodingSupportImpl.EncodingWithQ;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static io.helidon.http.encoding.ContentEncodingSupportImpl.encodings;

class ContentEncodingSupportTest {

    @Test
    void testEncodingsParserOne() {
        List<EncodingWithQ> encodings = encodings("gzip");
        assertThat(encodings.size(), is(1));
        assertThat(encodings.get(0).toString(), is("gzip;q=1.0"));
    }

    @Test
    void testEncodingsParserMany() {
        List<EncodingWithQ> encodings = encodings("gzip,compress,  br  ");
        assertThat(encodings.size(), is(3));
        assertThat(encodings.get(0).toString(), is("gzip;q=1.0"));
        assertThat(encodings.get(1).toString(), is("compress;q=1.0"));
        assertThat(encodings.get(2).toString(), is("br;q=1.0"));
    }

    @Test
    void testEncodingsParserWithQs() {
        List<EncodingWithQ> encodings = encodings("gzip;q=1.0, deflate;q=0.6,  identity;q=0.3");
        assertThat(encodings.size(), is(3));
        assertThat(encodings.get(0).toString(), is("gzip;q=1.0"));
        assertThat(encodings.get(1).toString(), is("deflate;q=0.6"));
        assertThat(encodings.get(2).toString(), is("identity;q=0.3"));
    }
}
