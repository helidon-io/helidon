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

package io.helidon.service.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_GENERATE_BINDING;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_BINDING_EMPTY;

/**
 * A {@link java.util.ServiceLoader} provider implementation for {@link io.helidon.codegen.spi.CodegenExtensionProvider}
 * that handles generation of ApplicationBinding class during annotation processing based on the GenerateBinding annotation.
 */
public class ServiceBindingCodegenProvider implements CodegenExtensionProvider {
    /**
     * Default binding class name.
     */
    public static final String BINDING_CLASS_NAME = "ApplicationBinding";

    /**
     * Public constructor required by Java {@link java.util.ServiceLoader}.
     */
    public ServiceBindingCodegenProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(SERVICE_ANNOTATION_GENERATE_BINDING);
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new ServiceBindingCodegen();
    }

    /*
    Generates an empty ApplicationBinding class (customizable name) in the package of the annotated type.
    This can only happen once per runtime. Duplicate annotations will throw an exception.
     */
    private static class ServiceBindingCodegen implements CodegenExtension {
        private static final TypeName GENERATOR = TypeName.create(ServiceBindingCodegen.class);

        private final List<BindingInfo> annotations = new ArrayList<>();

        private ServiceBindingCodegen() {
        }

        @Override
        public void process(RoundContext roundContext) {
            roundContext.annotatedTypes(SERVICE_ANNOTATION_GENERATE_BINDING)
                    .stream()
                    .map(this::toInfo)
                    .forEach(annotations::add);
        }

        @Override
        public void processingOver(RoundContext roundContext) {
            Map<TypeName, BindingInfo> allAnnotatedTypes = new LinkedHashMap<>();
            annotations.forEach(it -> allAnnotatedTypes.put(it.declaringType, it));

            if (allAnnotatedTypes.isEmpty()) {
                return;
            }

            if (allAnnotatedTypes.size() > 1) {
                throw new CodegenException("Annotation " + SERVICE_ANNOTATION_GENERATE_BINDING + " can only be"
                                                   + " used once in an application. Found in: " + allAnnotatedTypes.keySet(),
                                           allAnnotatedTypes.values().iterator().next().typeInfo.originatingElementValue());
            }

            // there is a single one, generate the type
            BindingInfo first = annotations.getFirst();
            TypeName generatedType = first.generatedType();
            TypeName triggerType = first.typeInfo().typeName();
            String name = first.typeInfo().module().orElse("unnamed") + "/" + generatedType.packageName();

            var classModel = ClassModel.builder()
                    .type(generatedType)
                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .superType(SERVICE_BINDING_EMPTY)
                    .copyright(CodegenUtil.copyright(GENERATOR,
                                                     triggerType,
                                                     generatedType))
                    .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                                   triggerType,
                                                                   generatedType,
                                                                   "1",
                                                                   ""))
                    .addConstructor(ctr -> ctr
                            .accessModifier(AccessModifier.PRIVATE)
                            .addContent("super(\"")
                            .addContent(name)
                            .addContentLine("\");"))
                    .addMethod(create -> create
                            .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                            .isStatic(true)
                            .returnType(generatedType)
                            .name("create")
                            .addContent("return new ")
                            .addContent(generatedType.className())
                            .addContentLine("();")
                    )
                    .addMethod(configure -> configure
                            .addAnnotation(Annotations.OVERRIDE)
                            .accessModifier(AccessModifier.PUBLIC)
                            .name("configure")
                            .addParameter(builder -> builder
                                    .name("builder")
                                    .type(ServiceCodegenTypes.SERVICE_CONFIG_BUILDER))
                            .addContentLine("warnEmpty();")
                            .addContentLine("builder.discoverServices(true);")
                            .addContentLine("builder.discoverServicesFromServiceLoader(true);")
                    );

            roundContext.addGeneratedType(generatedType,
                                          classModel,
                                          first.declaringType(),
                                          first.typeInfo().originatingElementValue());
        }

        private BindingInfo toInfo(TypeInfo typeInfo) {
            var annotation = typeInfo.annotation(SERVICE_ANNOTATION_GENERATE_BINDING);
            var declaringType = typeInfo.typeName();

            String className = annotation.value().orElse(BINDING_CLASS_NAME);
            String packageName = annotation.stringValue("packageName")
                    .filter(it -> !it.equals("@default"))
                    .orElse(declaringType.packageName());
            return new BindingInfo(typeInfo, declaringType, TypeName.builder()
                    .className(className)
                    .packageName(packageName)
                    .build());
        }
    }

    private record BindingInfo(TypeInfo typeInfo, TypeName declaringType, TypeName generatedType) {
    }
}
