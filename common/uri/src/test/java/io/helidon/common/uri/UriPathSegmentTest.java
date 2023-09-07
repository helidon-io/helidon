/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.parameters.Parameters;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UriPathSegmentTest {
    @Test
    void testSimplePath() {
        String rawPath = "plaintext";
        UriPathSegment segment = UriPathSegment.create(rawPath);

        assertThat(segment.rawValue(), is(rawPath));
        assertThat(segment.value(), is(rawPath));
        assertThat(segment.rawValueNoParams(), is(rawPath));
        assertThat(segment.matrixParameters().isEmpty(), is(true));
    }

    @Test
    void testPathWithParams() {
        String rawPath = "plaintext;v=1.0;a=b;b=1,2;c";
        UriPathSegment segment = UriPathSegment.create(rawPath);

        assertThat(segment.rawValue(), is(rawPath));
        assertThat(segment.value(), is("plaintext"));
        assertThat(segment.rawValueNoParams(), is("plaintext"));

        Parameters params = segment.matrixParameters();
        assertThat(params.isEmpty(), is(false));
        assertThat(params.get("v"), is("1.0"));
        assertThat(params.get("a"), is("b"));
        assertThat(params.get("b"), is("1"));
        assertThat(params.get("c"), is(""));
    }

    @Test
    void testPathWithParamsAndEncoding() {
        String rawPath = "pla%20i%2Fn%3Btext;v=1.0;a=b;b=1,2;c";
        UriPathSegment segment = UriPathSegment.create(rawPath);

        assertThat(segment.rawValue(), is(rawPath));
        assertThat(segment.value(), is("pla i/n;text"));
        assertThat(segment.rawValueNoParams(), is("pla%20i%2Fn%3Btext"));

        Parameters params = segment.matrixParameters();
        assertThat(params.isEmpty(), is(false));
        assertThat(params.get("v"), is("1.0"));
        assertThat(params.get("a"), is("b"));
        assertThat(params.get("b"), is("1"));
        assertThat(params.get("c"), is(""));
    }
}