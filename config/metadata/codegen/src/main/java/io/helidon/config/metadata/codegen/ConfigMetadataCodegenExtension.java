/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.codegen;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.TypeName;
import io.helidon.config.metadata.model.CmModel;
import io.helidon.config.metadata.model.CmModel.CmModule;
import io.helidon.config.metadata.model.CmModel.CmType;

class ConfigMetadataCodegenExtension implements CodegenExtension {
    private static final String META_FILE = "META-INF/helidon/config-metadata.json";

    private final Set<TypeName> types = new HashSet<>();
    private final Map<String, Set<CmType>> modules = new TreeMap<>();
    private final CodegenContext ctx;

    ConfigMetadataCodegenExtension(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        // we may have multiple rounds, let's collect what we can
        // the type info may change (i.e. we code generate something that is not available in the first round)
        for (var typeInfo : roundContext.annotatedTypes(ConfigMetadataTypes.CONFIGURED)) {
            types.add(typeInfo.typeName());
        }
    }

    @Override
    public void processingOver(RoundContext rc) {
        var handler = new TypeHandler(ctx);
        for (var e : types) {
            ctx.typeInfo(e).ifPresent(handler::handle);
        }
        handler.models().forEach((typeInfo, model) -> {
            var module = typeInfo.module().orElse("unnamed module");
            modules.computeIfAbsent(module, k -> new TreeSet<>()).add(model);
        });
        storeMetadata();
    }

    private void storeMetadata() {
        if (!modules.isEmpty()) {
            var model = CmModel.of(modules.entrySet().stream()
                    .map(e -> CmModule.of(e.getKey(), new ArrayList<>(e.getValue())))
                    .toList());
            var baos = new ByteArrayOutputStream();
            try (var w = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
                var jsonArray = model.toJson();
                jsonArray.write(w);
            }
            ctx.filer().writeResource(baos.toByteArray(), META_FILE);
        }
    }
}
