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

package io.helidon.quic;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class QuicModuleExportsTest {

    @Test
    void shouldExportOnlyExtensionPackages() throws Exception {
        Path modulePath = Path.of(QuicConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ModuleDescriptor descriptor = ModuleFinder.of(modulePath)
                .find("io.helidon.quic")
                .orElseThrow()
                .descriptor();
        Set<String> exportedPackages = descriptor.exports()
                .stream()
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        Set<String> unqualifiedExports = descriptor.exports()
                .stream()
                .filter(export -> !export.isQualified())
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());
        Set<String> transitiveRequires = descriptor.requires()
                .stream()
                .filter(require -> require.modifiers().contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE))
                .map(ModuleDescriptor.Requires::name)
                .collect(Collectors.toSet());
        ModuleDescriptor.Exports streamExport = descriptor.exports()
                .stream()
                .filter(export -> export.source().equals("io.helidon.quic.stream"))
                .findFirst()
                .orElseThrow();

        assertThat(exportedPackages, not(hasItems("io.helidon.quic.packet", "io.helidon.quic.frame")));
        assertThat(unqualifiedExports, containsInAnyOrder("io.helidon.quic"));
        assertThat(transitiveRequires,
                   hasItems("io.helidon.builder.api",
                            "io.helidon.common.buffers",
                            "io.helidon.common.socket",
                            "io.helidon.common.tls",
                            "io.helidon.config"));
        assertThat(streamExport.isQualified(), is(true));
        assertThat(streamExport.targets(),
                   containsInAnyOrder("io.helidon.http.http3",
                                      "io.helidon.webclient.http3",
                                      "io.helidon.webserver.http3",
                                      "io.helidon.webserver.quic",
                                      "io.helidon.webserver.testing.junit5.http3"));
    }
}
