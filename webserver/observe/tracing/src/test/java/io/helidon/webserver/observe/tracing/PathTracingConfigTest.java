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

package io.helidon.webserver.observe.tracing;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.uri.UriPath;
import io.helidon.config.Config;
import io.helidon.http.Method;
import io.helidon.tracing.config.TracingConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PathTracingConfigTest {

    @Test
    void configuredMethodUsesExactMatching() {
        var config = PathTracingConfig.create(Config.just("""
                path: /greet
                methods: [get]
                """, MediaTypes.APPLICATION_YAML));

        assertExactLowercaseMatch(config);
    }

    @Test
    void programmaticMethodUsesExactMatching() {
        var config = PathTracingConfig.builder()
                .path("/greet")
                .addMethod("get")
                .tracingConfig(TracingConfig.builder().build())
                .build();

        assertExactLowercaseMatch(config);
    }

    private static void assertExactLowercaseMatch(PathTracingConfig config) {
        assertThat("Different-case method matching", config.matches(Method.GET, UriPath.create("/greet")), is(false));
        assertThat("Exact method matching",
                   config.matches(Method.create("get"), UriPath.create("/greet")),
                   is(true));
    }
}
