/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hjson.JArray;
import io.helidon.metadata.hjson.JObject;

class ConfigMetadataCodegenExtension implements CodegenExtension {
    /*
     * Configuration metadata file location.
     */
    private static final String META_FILE = "META-INF/helidon/config-metadata.json";

    private final Set<TypeName> blueprints = new HashSet<>();
    private final Set<TypeName> configMetadata = new HashSet<>();
    // Newly created options as part of this processor run - these will be stored to META_FILE
    // map of type name to its configured type
    private final Map<TypeName, ConfiguredType> newOptions = new HashMap<>();
    // map of module name to list of classes that belong to it
    private final Map<String, List<TypeName>> moduleTypes = new HashMap<>();

    private final CodegenContext ctx;

    ConfigMetadataCodegenExtension(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        // we may have multiple rounds, let's collect what we can
        // the type info may change (i.e. we code generate something that is not available in the
        // first round)
        roundContext.annotatedTypes(ConfigMetadataTypes.CONFIGURED)
                .forEach(it -> blueprints.add(it.typeName()));

        roundContext.annotatedTypes(ConfigMetadataTypes.META_CONFIGURED)
                .forEach(it -> configMetadata.add(it.typeName()));
    }

    @Override
    public void processingOver(RoundContext roundContext) {
        Stream.concat(typesToProcess(blueprints)
                              .map(it -> TypeHandlerBuilderApi.create(ctx, it)),
                      typesToProcess(configMetadata)
                              .map(it -> TypeHandlerMetaApi.create(ctx, it)))
                .map(TypeHandler::handle)
                .forEach(it -> {
                    TypeName targetType = it.targetType();
                    newOptions.put(targetType, it.configuredType());
                    moduleTypes.computeIfAbsent(it.moduleName(),
                                                ignored -> new ArrayList<>())
                            .add(targetType);
                });

        storeMetadata();
    }

    private Stream<TypeInfo> typesToProcess(Set<TypeName> typeNames) {
        return typeNames.stream()
                .map(ctx::typeInfo)
                .flatMap(Optional::stream);
    }

    private void storeMetadata() {
        List<JObject> root = new ArrayList<>();

        for (var module : moduleTypes.entrySet()) {
            String moduleName = module.getKey();
            var types = module.getValue();
            List<JObject> typeArray = new ArrayList<>();
            types.forEach(it -> newOptions.get(it).write(typeArray));
            root.add(JObject.builder()
                             .set("module", moduleName)
                             .setObjects("types", typeArray)
                             .build());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter w = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            JArray.create(root).write(w);
        }
        ctx.filer().writeResource(baos.toByteArray(), META_FILE);
    }
}
