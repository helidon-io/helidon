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

package io.helidon.service.tests.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.service.registry.DependencyPlanBinder;
import io.helidon.service.registry.EventManager;
import io.helidon.service.registry.RegistryStartupProvider;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@Testing.Test
class ServiceLoaderCodegenTest {
    private static final String HEADER =
            "# List of service contracts we want to support either from service registry, or from service loader";
    private static final String MODULE_NAME = "io.helidon.service.tests.codegen";
    private static final Path METADATA = Path.of("target", "classes", "META-INF", "helidon");

    @Test
    void generatesServiceLoaderMetadataFromModuleUses() throws IOException {
        Path serviceLoader = METADATA.resolve(MODULE_NAME)
                .resolve("service.loader");
        List<String> lines = Files.readAllLines(serviceLoader);

        assertThat(lines,
                   contains(HEADER,
                            DependencyPlanBinder.class.getName(),
                            EventManager.class.getName(),
                            RegistryStartupProvider.class.getName()));
        assertThat(Files.readAllLines(METADATA.resolve("manifest")),
                   contains("#HELIDON MANIFEST#",
                            "META-INF/helidon/" + MODULE_NAME + "/service.loader"));
    }
}
