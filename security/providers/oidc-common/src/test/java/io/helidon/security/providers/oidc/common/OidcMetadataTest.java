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

package io.helidon.security.providers.oidc.common;

import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.oidc.common.OidcMetadata.Builder.fixPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OidcMetadataTest {
    @Test
    void testFixPath() {
        assertThat(fixPath(null, ".metadata"), is("/.metadata"));
        assertThat(fixPath("/", ".metadata"), is("/.metadata"));
        assertThat(fixPath("/subpath", ".metadata"), is("/subpath/.metadata"));
        assertThat(fixPath("/subpath/", ".metadata"), is("/subpath/.metadata"));
        assertThat(fixPath("/subpath", "/.metadata"), is("/subpath/.metadata"));
        assertThat(fixPath("/subpath/", "/.metadata"), is("/subpath/.metadata"));
    }
}