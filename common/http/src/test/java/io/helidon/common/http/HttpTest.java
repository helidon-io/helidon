/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import static io.helidon.common.http.Http.Status.TEMPORARY_REDIRECT_307;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Http}.
 */
class HttpTest {
    @Test
    void testResponseStatusIsStatus() {
        Http.ResponseStatus rs = Http.ResponseStatus.create(TEMPORARY_REDIRECT_307.code());
        assertThat(rs, sameInstance(TEMPORARY_REDIRECT_307));
    }

    @Test
    void testResponseStatusWithReasonIsStatus() {
        Http.ResponseStatus rs = Http.ResponseStatus
                .create(TEMPORARY_REDIRECT_307.code(), TEMPORARY_REDIRECT_307.reasonPhrase().toUpperCase());
        assertThat(rs, sameInstance(TEMPORARY_REDIRECT_307));
    }

    @Test
    void testResposneStatusCustomReason() {
        Http.ResponseStatus rs = Http.ResponseStatus
                .create(TEMPORARY_REDIRECT_307.code(), "Custom reason phrase");
        assertThat(rs, not(TEMPORARY_REDIRECT_307));
        assertThat(rs.reasonPhrase(), is("Custom reason phrase"));
        assertThat(rs.code(), is(TEMPORARY_REDIRECT_307.code()));
        assertThat(rs.family(), is(TEMPORARY_REDIRECT_307.family()));
    }
}