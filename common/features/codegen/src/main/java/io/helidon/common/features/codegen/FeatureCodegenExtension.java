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

package io.helidon.common.features.codegen;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.FilerResource;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.features.metadata.Aot;
import io.helidon.common.features.metadata.Deprecation;
import io.helidon.common.features.metadata.FeatureMetadata;
import io.helidon.common.features.metadata.FeatureRegistry;
import io.helidon.common.features.metadata.FeatureStatus;
import io.helidon.common.features.metadata.Flavor;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ModuleTypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;

class FeatureCodegenExtension implements CodegenExtension {
    private final CodegenContext ctx;
    private final List<FeatureMetadata> processedModules = new ArrayList<>();

    FeatureCodegenExtension(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        for (ModuleTypeInfo module : roundContext.modules()) {
            validate(module);
            process(module);
        }
    }

    @Override
    public void processingOver(RoundContext roundContext) {
        if (processedModules.isEmpty()) {
            return;
        }

        List<Hson.Struct> features = processedModules.stream()
                .map(FeatureMetadata::toHson)
                .collect(Collectors.toUnmodifiableList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var pw = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            Hson.Array array = Hson.Array.create(features);
            array.write(pw);
        }

        CodegenFiler filer = ctx.filer();
        FilerResource resource = filer.resource(FeatureRegistry.FEATURE_REGISTRY_LOCATION_V2);
        resource.bytes(baos.toByteArray());
        resource.write();
    }

    private void process(ModuleTypeInfo module) {
        FeatureMetadata.Builder builder = FeatureMetadata.builder()
                .module(module.name());

        builder.name(module.annotation(FeatureCodegenTypes.NAME)
                             .value()
                             .orElseThrow(() -> new CodegenException("Failed to get annotation value "
                                                                             + FeatureCodegenTypes.NAME.fqName(),
                                                                     module.originatingElementValue())));

        module.findAnnotation(FeatureCodegenTypes.DESCRIPTION)
                .flatMap(Annotation::value)
                .ifPresent(builder::description);

        module.findAnnotation(FeatureCodegenTypes.SINCE)
                .flatMap(Annotation::value)
                .ifPresent(builder::since);

        module.findAnnotation(FeatureCodegenTypes.PATH)
                .flatMap(Annotation::stringValues)
                .ifPresent(builder::path);

        module.findAnnotation(FeatureCodegenTypes.FLAVOR)
                .flatMap(it -> it.stringValues())
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addFlavor);

        module.findAnnotation(FeatureCodegenTypes.INVALID_FLAVOR)
                .flatMap(it -> it.stringValues())
                .stream()
                .flatMap(List::stream)
                .map(Flavor::valueOf)
                .forEach(builder::addFlavor);

        if (module.hasAnnotation(FeatureCodegenTypes.INCUBATING)) {
            builder.status(FeatureStatus.INCUBATING);
        }
        if (module.hasAnnotation(FeatureCodegenTypes.PREVIEW)) {
            builder.status(FeatureStatus.PREVIEW);
        }
        TypeName deprecatedType = TypeName.create(Deprecated.class);
        if (module.hasAnnotation(deprecatedType)) {
            builder.status(FeatureStatus.DEPRECATED);
            Deprecation.Builder deprecation = Deprecation.builder()
                    .isDeprecated(true);

            Annotation annotation = module.annotation(deprecatedType);
            annotation.stringValue("since")
                    .filter(Predicate.not(String::isBlank))
                    .ifPresent(deprecation::since);
            deprecationMessage(module)
                    .ifPresent(deprecation::description);
            builder.deprecation(deprecation.build());
        }
        if (module.hasAnnotation(FeatureCodegenTypes.AOT)) {
            Annotation annotation = module.annotation(FeatureCodegenTypes.AOT);

            Aot.Builder aot = Aot.builder();
            aot.supported(annotation.booleanValue().orElse(true));
            annotation.stringValue("description")
                    .ifPresent(aot::description);
            builder.aot(aot.build());
        }

        this.processedModules.add(builder.build());
    }

    private Optional<String> deprecationMessage(ModuleTypeInfo module) {
        Optional<String> description = module.description();
        if (description.isEmpty()) {
            return Optional.empty();
        }
        Javadoc javadoc = Javadoc.parse(description.get());
        if (javadoc.deprecation().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join("\n", javadoc.deprecation()));
    }

    private void validate(ModuleTypeInfo module) {
        // there MUST be at least @Features.Name
        if (!module.hasAnnotation(FeatureCodegenTypes.NAME)) {
            throw new CodegenException("Feature must have at least " + FeatureCodegenTypes.NAME.fqName() + " annotation",
                                       module.originatingElementValue());
        }
        if (module.hasAnnotation(FeatureCodegenTypes.INCUBATING)
                && module.hasAnnotation(FeatureCodegenTypes.PREVIEW)) {
            throw new CodegenException("Illegal combination of annotations, feature can be either Incubating, or Preview",
                                       module.originatingElementValue());
        }

        if (module.hasAnnotation(FeatureCodegenTypes.FLAVOR)
                && module.hasAnnotation(FeatureCodegenTypes.INVALID_FLAVOR)) {
            List<Flavor> in = module.annotation(FeatureCodegenTypes.FLAVOR)
                    .stringValues()
                    .stream()
                    .flatMap(List::stream)
                    .map(Flavor::valueOf)
                    .toList();
            List<Flavor> notIn = module.annotation(FeatureCodegenTypes.INVALID_FLAVOR)
                    .stringValues()
                    .stream()
                    .flatMap(List::stream)
                    .map(Flavor::valueOf)
                    .toList();
            for (Flavor helidonFlavor : in) {
                if (notIn.contains(helidonFlavor)) {
                    throw new CodegenException("Illegal combination of flavors, where " + helidonFlavor + " is configured"
                                                       + " both as valid and invalid",
                                               module.originatingElementValue());
                }
            }
        }
    }
}
