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

package io.helidon.common.features.codegen;

import java.io.IOException;
import java.nio.file.Files;

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.features.api.Features;
import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureRegistry;
import io.helidon.common.features.metadata.Flavor;
import io.helidon.metadata.MetadataConstants;
import io.helidon.metadata.hson.Hson;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class FeatureCodegenExtensionTest {

    @Test
    void invalidFlavorIsGeneratedAsInvalidFlavor() throws IOException {
        var result = compile("@Features.InvalidFlavor(HelidonFlavor.MP)");

        FeatureMetadata metadata = metadata(result);
        assertThat(metadata.flavors(), not(hasItem(Flavor.MP)));
        assertThat(metadata.invalidFlavors(), contains(Flavor.MP));
    }

    @Test
    void disjointFlavorsAreGenerated() throws IOException {
        var result = compile("""
                @Features.Flavor(HelidonFlavor.SE)
                @Features.InvalidFlavor(HelidonFlavor.MP)
                """);

        FeatureMetadata metadata = metadata(result);
        assertThat(metadata.flavors(), contains(Flavor.SE));
        assertThat(metadata.invalidFlavors(), contains(Flavor.MP));
    }

    @Test
    void conflictingFlavorsAreRejected() {
        var result = compile("""
                @Features.Flavor(HelidonFlavor.SE)
                @Features.InvalidFlavor(HelidonFlavor.SE)
                """);

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("SE is configured both as valid and invalid"));
    }

    private static TestCompiler.Result compile(String annotations) {
        return TestCompiler.builder()
                .currentRelease()
                .addModulepath(Features.class)
                .addModulepath(FeatureMetadata.class)
                .addModulepath(io.helidon.common.Builder.class)
                .addModulepath(MetadataConstants.class)
                .addModulepath(Hson.class)
                .addModulepath(BufferData.class)
                .addProcessor(AptProcessor::new)
                .printDiagnostics(false)
                .addSource("module-info.java", """
                        import io.helidon.common.features.api.Features;
                        import io.helidon.common.features.api.HelidonFlavor;

                        @Features.Name("Test Feature")
                        %s
                        module test.module {
                            requires io.helidon.common.features.api;
                        }
                        """.formatted(annotations))
                .build()
                .compile();
    }

    private static FeatureMetadata metadata(TestCompiler.Result result) throws IOException {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));

        var registry = result.classOutput()
                .resolve(MetadataConstants.LOCATION)
                .resolve("test.module")
                .resolve(MetadataConstants.FEATURE_REGISTRY_FILE);
        assertThat(Files.exists(registry), is(true));

        try (var input = Files.newInputStream(registry)) {
            return FeatureRegistry.metadata("test", Hson.parse(input).asArray())
                    .getFirst();
        }
    }
}
