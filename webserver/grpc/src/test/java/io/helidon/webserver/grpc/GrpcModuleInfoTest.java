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

package io.helidon.webserver.grpc;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;

import io.helidon.webserver.grpc.spi.GrpcServerServiceProvider;
import org.junit.jupiter.api.Test;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcModuleInfoTest {
    @Test
    void declaresGrpcServiceProviderUse() throws IOException {
        ModuleDescriptor descriptor;
        try (var input = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
            descriptor = ModuleDescriptor.read(input);
        }

        assertThat(descriptor.uses(), hasItem(GrpcServerServiceProvider.class.getName()));
    }

    @Test
    void serviceRegistryRequirementIsTransitive() throws IOException {
        ModuleDescriptor descriptor;
        try (var input = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
            descriptor = ModuleDescriptor.read(input);
        }

        ModuleDescriptor.Requires serviceRegistry = descriptor.requires()
                .stream()
                .filter(requires -> requires.name().equals("io.helidon.service.registry"))
                .findFirst()
                .orElseThrow();
        assertThat(serviceRegistry.modifiers(), hasItem(TRANSITIVE));
    }
}
