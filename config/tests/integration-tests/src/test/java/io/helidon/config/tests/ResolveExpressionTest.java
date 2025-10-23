/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.config.tests;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigBuilderSupport.resolveExpression;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
Test of a type in common config, but that requires actual config instance, so it is located here
 */
class ResolveExpressionTest {
    @Test
    void testResolveExpressionAll() {
        var config = Config.just(ConfigSources.create(Map.of("my-service.schema", "https",
                                                             "my-service.host", "www.examples.com",
                                                             "my-service.port", "8081")));

        String resolved = resolveExpression(config,
                                            "${my-service.schema:http}://${my-service.host:localhost}:${my-service"
                                                    + ".port:8080}/service");

        assertThat(resolved, is("https://www.examples.com:8081/service"));
    }

    @Test
    void testResolveExpressionPrefix() {
        var config = Config.just(ConfigSources.create(Map.of("my-service.schema", "https",
                                                             "my-service.host", "www.examples.com",
                                                             "my-service.port", "8081")));

        String resolved = resolveExpression(config,
                                            "endpoint://${my-service.schema:http}://${my-service.host:localhost}:${my-service"
                                                    + ".port:8080}/service");

        assertThat(resolved, is("endpoint://https://www.examples.com:8081/service"));
    }

    @Test
    void testResolveExpressionCombined() {
        var config = Config.just(ConfigSources.create(Map.of("my-service.schema", "https",
                                                             "my-service.port", "8081")));

        String resolved = resolveExpression(config,
                                            "${my-service.schema:http}://${my-service.host:localhost}:${my-service"
                                                    + ".port:8080}/service");

        assertThat(resolved, is("https://localhost:8081/service"));
    }

    @Test
    void testResolveExpressionNone() {
        var config = Config.empty();

        String resolved = resolveExpression(config,
                                            "${my-service.schema:http}://${my-service.host:localhost}:${my-service"
                                                    + ".port:8080}/service");

        assertThat(resolved, is("http://localhost:8080/service"));
    }

    @Test
    void testResolveExpressionNoDefaultNone() {
        var config = Config.empty();

        assertThrows(ConfigException.class, () -> resolveExpression(config,
                                                                    "${my-service.schema:http}://${my-service"
                                                                            + ".host:localhost}:${my-service.port}/service"));

    }
}
