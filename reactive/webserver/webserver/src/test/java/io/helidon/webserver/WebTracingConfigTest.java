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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.helidon.tracing.config.TracingConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class WebTracingConfigTest {

    /**
     * Tests default exclusions for MP paths such as "/health", etc.
     */
    @Test
    void testDefaultPaths() {
        // Collect all path configs
        WebTracingConfig tracingConfig = WebTracingConfig.builder().build();
        List<PathTracingConfig> pathConfigs = new ArrayList<>();
        tracingConfig.pathConfigs().forEach(pathConfigs::add);

        // Check list of disabled paths
        List<String> paths = pathConfigs.stream().map(PathTracingConfig::path).collect(Collectors.toList());
        assertThat(paths.size(), is(5));
        assertThat(paths, contains("/metrics", "/metrics/{+}", "/health", "/health/{+}", "/openapi"));

        // Check they are all disabled
        long ignored = pathConfigs.stream()
                .map(PathTracingConfig::tracedConfig)
                .filter(tc -> tc.equals(TracingConfig.DISABLED))
                .count();
        assertThat(ignored, is(5L));
    }

    /**
     * Tests a default override where "/openapi" is enabled.
     */
    @Test
    void testEnableHealth() {
        // Collect all path configs
        WebTracingConfig tracingConfig = WebTracingConfig.builder()
                .addPathConfig(PathTracingConfig.builder()
                        .path("/openapi")
                        .tracingConfig(TracingConfig.ENABLED)
                        .build())
                .build();
        List<PathTracingConfig> pathConfigs = new ArrayList<>();
        tracingConfig.pathConfigs().forEach(pathConfigs::add);

        // Get list of /openapi paths
        List<PathTracingConfig> healthPathConfigs = pathConfigs
                .stream()
                .filter(p -> p.path().equals("/openapi"))
                .collect(Collectors.toList());
        assertThat(healthPathConfigs.size(), is(2));
        assertThat(healthPathConfigs.get(0).path(), is("/openapi"));
        assertThat(healthPathConfigs.get(0).tracedConfig(), is(TracingConfig.DISABLED));
        assertThat(healthPathConfigs.get(1).path(), is("/openapi"));
        assertThat(healthPathConfigs.get(1).tracedConfig(), is(TracingConfig.ENABLED));
    }
}
