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

package io.helidon.declarative.codegen.openapi;

import java.util.Optional;

import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

final class OpenApiExtension implements RegistryCodegenExtension {
    private final Optional<OpenApiSourceGenerator> sourceGenerator;

    OpenApiExtension(RegistryCodegenContext ctx) {
        this.sourceGenerator = OpenApiSourceGenerator.create(ctx);
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        sourceGenerator.ifPresent(generator -> generator.processDocuments(roundContext));
    }
}
