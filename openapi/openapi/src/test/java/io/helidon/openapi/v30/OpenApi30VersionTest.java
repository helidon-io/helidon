/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi.v30;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApi30VersionTest {
    @Test
    void validatesConfiguredVersionFamily() {
        assertThat(OpenApi30Version.builder().version("3.0.99").build().version(), is("3.0.99"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> OpenApi30Version.builder().version("3.1.0").build());
        assertThat(ex.getMessage(), containsString("3.0"));
        assertThat(ex.getMessage(), containsString("3.1.0"));
    }
}
