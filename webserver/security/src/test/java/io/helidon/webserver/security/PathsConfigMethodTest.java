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

package io.helidon.webserver.security;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.Method;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PathsConfigMethodTest {

    @Test
    void configuredMethodUsesExactMatching() {
        var config = PathsConfig.create(Config.just("""
                path: /greet
                methods: [get]
                """, MediaTypes.APPLICATION_YAML));

        assertExactLowercaseMatch(config);
    }

    @Test
    void programmaticMethodUsesExactMatching() {
        var config = PathsConfig.builder()
                .path("/greet")
                .addMethod(Method.create("get"))
                .handler(SecurityHandler.create())
                .build();

        assertExactLowercaseMatch(config);
    }

    private static void assertExactLowercaseMatch(PathsConfig config) {
        var predicate = Method.predicate(config.methods());

        assertThat("Different-case method matching", predicate.test(Method.GET), is(false));
        assertThat("Exact method matching", predicate.test(Method.create("get")), is(true));
    }
}
