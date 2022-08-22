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

package io.helidon.common.uri;

import org.junit.jupiter.api.Test;

import static io.helidon.common.uri.UriPathHelper.stripMatrixParams;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class UriPathHelperTest {

    @Test
    void testStripPathParams() {
        String expected = "/users/user/john";

        String actual = stripMatrixParams("/users;domain=helidon/user;source=cz;type=main/john;alt=John");
        assertThat(actual, is(expected));

        // the method is expected to remove path parameters, it does not attempt
        // to "fix" the path, nor does it take care of queries
        assertThat(stripMatrixParams("/admin/list"), is("/admin/list"));
        assertThat(stripMatrixParams("/admin;a=b"), is("/admin"));
        assertThat(stripMatrixParams("/admin;a=b/list;c=d;e=f"), is("/admin/list"));
        assertThat(stripMatrixParams("/admin;a=b/list;c=d;e=f;"), is("/admin/list"));
        assertThat(stripMatrixParams("/admin;"), is("/admin"));
        assertThat(stripMatrixParams("/admin;/list"), is("/admin/list"));
        assertThat(stripMatrixParams("/admin/;/list/"), is("/admin//list/"));
        assertThat(stripMatrixParams("/;a=b"), is("/"));
        assertThat(stripMatrixParams(";a=b;c=d;"), is(""));
        assertThat(stripMatrixParams("/admin/;"), is("/admin/"));
        assertThat(stripMatrixParams("/admin//;"), is("/admin//"));
        assertThat(stripMatrixParams("/;"), is("/"));
        assertThat(stripMatrixParams(";"), is(""));
        assertThat(stripMatrixParams(";/admin/"), is("/admin/"));
    }
}