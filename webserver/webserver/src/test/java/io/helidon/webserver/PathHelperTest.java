/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static io.helidon.webserver.PathHelper.extractPathParams;

class PathHelperTest {

    @Test
    void ignorePathParamsMatch() {
        assertThat(extractPathParams("/admin/list"), is("/admin/list"));
        assertThat(extractPathParams("/admin;a=b"), is("/admin"));
        assertThat(extractPathParams("/admin;a=b/list;c=d;e=f"), is("/admin/list"));
        assertThat(extractPathParams("/admin;a=b/list;c=d;e=f;"), is("/admin/list"));
        assertThat(extractPathParams("/admin;"), is("/admin"));
        assertThat(extractPathParams("/admin;/list"), is("/admin/list"));
        assertThat(extractPathParams("/admin/;/list/"), is("/admin//list"));
        assertThat(extractPathParams("/;a=b"), is("/"));
        assertThat(extractPathParams(";a=b;c=d;"), is("/"));
        assertThat(extractPathParams("/admin/;"), is("/admin"));
        assertThat(extractPathParams("/admin//;"), is("/admin/"));
        assertThat(extractPathParams("/;"), is("/"));
        assertThat(extractPathParams(";"), is("/"));
        assertThat(extractPathParams(";/admin/"), is("/admin"));
        assertThat(extractPathParams("/admin;a=b?b=c"), is("/admin?b=c"));
    }
}
