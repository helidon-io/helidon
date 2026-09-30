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

package io.helidon.telemetry.otelconfig;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.STATIC;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestModuleInfo {

    @Test
    void testZipkinDependenciesAreOptionalAndTransitive() throws URISyntaxException {
        Path modulePath = Path.of(OpenTelemetryConfig.class.getProtectionDomain()
                                          .getCodeSource()
                                          .getLocation()
                                          .toURI());
        ModuleDescriptor descriptor = ModuleFinder.of(modulePath)
                .find("io.helidon.telemetry.otelconfig")
                .orElseThrow()
                .descriptor();

        assertThat("Zipkin dependency modifiers",
                   requires(descriptor, "zipkin2").modifiers(),
                   is(Set.of(STATIC, TRANSITIVE)));
        assertThat("Zipkin reporter dependency modifiers",
                   requires(descriptor, "zipkin2.reporter").modifiers(),
                   is(Set.of(STATIC, TRANSITIVE)));
    }

    private static ModuleDescriptor.Requires requires(ModuleDescriptor descriptor, String moduleName) {
        return descriptor.requires()
                .stream()
                .filter(requirement -> requirement.name().equals(moduleName))
                .findFirst()
                .orElseThrow();
    }
}
