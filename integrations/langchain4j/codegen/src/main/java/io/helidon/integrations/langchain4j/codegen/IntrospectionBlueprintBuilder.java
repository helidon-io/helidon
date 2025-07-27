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

package io.helidon.integrations.langchain4j.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ClassType;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Returns;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.BLDR_PROTOTYPE_CONFIGURED;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.BLDR_REGISTRY_SUPPORT_ANNOTATION;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.BLDR_SINGULAR_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.BUILDER_BLUEPRINT;

abstract class IntrospectionBlueprintBuilder {

    private final ClassModel.Builder classModel = ClassModel.builder();
    private final ConfigureMethodBuilder confMethodBuilder;
    private final Map<String, TypedElementInfo> overrideProps;
    private final TypeInfo builderType;
    private final RoundContext ctx;
    private final TypeInfo typeInfo;
    private final TypeInfo parentTypeInfo;

    IntrospectionBlueprintBuilder(RoundContext ctx,
                                  TypeInfo typeInfo,
                                  TypeInfo parentTypeInfo,
                                  TypeInfo builderType) {
        this.ctx = ctx;
        this.typeInfo = typeInfo;
        this.parentTypeInfo = parentTypeInfo;
        this.builderType = builderType;
        this.overrideProps = resolveOverriddenProperties();
        this.confMethodBuilder = new ConfigureMethodBuilder(parentTypeInfo, builderType, typeInfo.typeName());
    }

    protected abstract Map<String, TypedElementInfo> resolveOverriddenProperties();

    protected abstract String configRoot();

    protected ClassModel.Builder classModelBuilder() {
        return classModel;
    }

    protected TypeName typeName() {
        return typeInfo.typeName();
    }

    protected TypeInfo parentTypeInfo() {
        return parentTypeInfo;
    }

    protected TypeInfo builderType() {
        return builderType;
    }

    protected void initClassModel(TypeName typeName, TypeName parentTypeName, Optional<TypeName> superTypeName) {
        var configRootField = Field.builder()
                .isStatic(true)
                .name("CONFIG_ROOT")
                .type(TypeNames.STRING)
                .addContentLiteral(configRoot())
                .addDescriptionLine("The root configuration key for this builder.")
                .build();

        superTypeName.ifPresent(classModelBuilder()::superType);

        classModelBuilder()
                .classType(ClassType.INTERFACE)
                .type(typeName)
                .copyright(CodegenUtil.copyright(ModelConfigCodegen.GENERATOR,
                                                 parentTypeName,
                                                 typeName))
                .addAnnotation(CodegenUtil.generatedAnnotation(ModelConfigCodegen.GENERATOR,
                                                               parentTypeName,
                                                               typeName,
                                                               "1",
                                                               ""))
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(BUILDER_BLUEPRINT))
                .addAnnotation(BLDR_REGISTRY_SUPPORT_ANNOTATION)
                .addField(configRootField)
                .addAnnotation(Annotation.builder()
                                       .typeName(BLDR_PROTOTYPE_CONFIGURED)
                                       .putProperty("value", AnnotationProperty.create("configRoot",
                                                                                       typeName,
                                                                                       configRootField.name()))
                                       .build());
    }

    protected Method.Builder createMethodBuilder(TypedElementInfo modelBldMethod) {
        var propType = modelBldMethod.enclosingType()
                .orElseThrow(() -> new CodegenException("No enclosing type for " + modelBldMethod.signature()));

        var b = Method.builder()
                .addAnnotation(Annotation.create(LangchainTypes.OPT_CONFIGURED))
                .description("Generated from {@link " + propType.fqName()
                        .replaceAll("\\$", ".") + "#" + modelBldMethod.signature().text() + "}");

        if (isInjectedByDefault(modelBldMethod)) {
            // Properties which have by default @Option.ServiceRegistry
            b.addAnnotation(Annotation.create(LangchainTypes.OPT_REGISTRY_SERVICE));
        }

        return b;
    }

    private static boolean isInjectedByDefault(TypedElementInfo modelBldMethod) {
        var s = modelBldMethod.signature();
        if (s.parameterTypes().size() != 1) {
            return false;
        }

        var propType = s.parameterTypes().getFirst();

        if (propType.equals(LangchainTypes.LC_DEF_REQUEST_PARAMS)) {
            return true;
        }
        if (propType.equals(LangchainTypes.LC_HTTP_CLIENT_BUILDER)) {
            return true;
        }
        if (propType.equals(LangchainTypes.LC_CHAT_MODEL_LISTENER)) {
            return true;
        }
        if (propType.equals(LIST)
                && propType.typeArguments().size() == 1
                && propType.typeArguments().getFirst().equals(LangchainTypes.LC_CHAT_MODEL_LISTENER)) {
            return true;
        }

        return false;
    }

    void addArrayProperty(String propName, TypeName propType, TypedElementInfo modelBldMethod, boolean skipBuilderMapping) {
        var methodBuilder = createMethodBuilder(modelBldMethod)
                .name(propName);

        methodBuilder.addAnnotation(BLDR_SINGULAR_ANNOTATION);

        methodBuilder.returnType(TypeName.builder(LIST).addTypeArgument(propType).build());

        if (!skipBuilderMapping) {
            confMethodBuilder.configureProperty(propName, propType, false, true, false);
        }

        if (overrideProps.containsKey(propName)) {
            confMethodBuilder.commentOverriddenProperty(overrideProps.get(propName));
        } else {
            classModelBuilder().addMethod(methodBuilder.build());
        }
    }

    void addOptionalProperty(String propName, TypeName propType, TypedElementInfo modelBldMethod, boolean skipBuilderMapping) {
        var methodBuilder = createMethodBuilder(modelBldMethod)
                .name(propName);

        methodBuilder.returnType(Returns.builder()
                                         .description(propType.className() + " property")
                                         .type(TypeName.builder(OPTIONAL)
                                                       .addTypeArgument(propType.boxed())
                                                       .build())
                                         .build());

        if (overrideProps.containsKey(propName)) {
            confMethodBuilder.commentOverriddenProperty(overrideProps.get(propName));
            if (!skipBuilderMapping) {
                confMethodBuilder.configureProperty(propName, propType, false, false,
                                                    // Overridden non-optional props are kept mandatory
                                                    overrideProps.get(propName).typeName().isOptional());
            }

        } else {
            classModelBuilder().addMethod(methodBuilder.build());
            if (!skipBuilderMapping) {
                confMethodBuilder.configureProperty(propName, propType, false, false, true);
            }
        }
    }

    void addCollectionProperty(String propName, TypeName propType, TypedElementInfo modelBldMethod, boolean skipBuilderMapping) {
        var methodBuilder = createMethodBuilder(modelBldMethod)
                .name(propName);

        methodBuilder
                .addAnnotation(BLDR_SINGULAR_ANNOTATION)
                .returnType(Returns.builder()
                                    .description(propType.className() + " property")
                                    .type(propType)
                                    .build());

        if (!skipBuilderMapping) {
            confMethodBuilder.configureProperty(propName, propType, false, false, false);
        }

        if (overrideProps.containsKey(propName)) {
            confMethodBuilder.commentOverriddenProperty(overrideProps.get(propName));
        } else {
            classModel.addMethod(methodBuilder.build());
        }

    }

    void addNestedBlueprintProperty(TypedElementInfo srcMethod,
                                    LlmNestedBlueprintBuilder nestedBluePrintBuilder,
                                    boolean skipBuilderMapping) {

        var propName = srcMethod.signature().name();
        var propType = srcMethod.signature().type();

        nestedBluePrintBuilder
                .introspectBuilder(List.of(), Set.of())
                .buildAndAdd();

        var methodBuilder = createMethodBuilder(srcMethod)
                .name(propName);

        methodBuilder.returnType(Returns.builder()
                                         .type(TypeName.builder(OPTIONAL)
                                                       .addTypeArgument(propType.boxed())
                                                       .build())
                                         .build());

        if (!skipBuilderMapping) {
            confMethodBuilder.configureProperty(propName, propType, true, false, true);
        }

        if (!overrideProps.containsKey(propName)) {
            classModelBuilder().addMethod(methodBuilder.build());
        }
    }

    void addNestedBlueprintCollectionProperty(TypedElementInfo srcMethod,
                                              LlmNestedBlueprintBuilder nestedBluePrintBuilder,
                                              boolean skipBuilderMapping) {

        var propName = srcMethod.signature().name();
        var propType = srcMethod.signature().type();

        nestedBluePrintBuilder
                .introspectBuilder(List.of(), Set.of())
                .buildAndAdd();

        var methodBuilder = createMethodBuilder(srcMethod)
                .name(propName);

        methodBuilder.addAnnotation(BLDR_SINGULAR_ANNOTATION);
        methodBuilder.returnType(propType);

        if (!skipBuilderMapping) {
            confMethodBuilder.configureProperty(propName, propType, true, false, false);
        }

        if (!overrideProps.containsKey(propName)) {
            classModelBuilder().addMethod(methodBuilder.build());
        }
    }

    void addNestedBlueprintArrayProperty(TypedElementInfo srcMethod,
                                         LlmNestedBlueprintBuilder nestedBluePrintBuilder,
                                         boolean skipBuilderMapping) {
        var propName = srcMethod.signature().name();
        var propType = srcMethod.signature().type();

        nestedBluePrintBuilder
                .introspectBuilder(List.of(), Set.of())
                .buildAndAdd();

        var methodBuilder = createMethodBuilder(srcMethod)
                .name(propName);

        methodBuilder.addAnnotation(BLDR_SINGULAR_ANNOTATION);

        methodBuilder.returnType(TypeName.builder(LIST).addTypeArgument(propType).build());

        if (!skipBuilderMapping) {
            confMethodBuilder.configureProperty(propName, propType, true, true, false);
        }

        if (!overrideProps.containsKey(propName)) {
            classModelBuilder().addMethod(methodBuilder.build());
        }
    }

    IntrospectionBlueprintBuilder introspectBuilder(List<String> skips, Set<String> nestedTypes) {
        List<TypeInfo> lineage = new ArrayList<>();
        lineage.add(builderType());
        for (Optional<TypeInfo> t = builderType().superTypeInfo();
                t.isPresent();
                t = t.get().superTypeInfo()) {
            lineage.add(t.get());
        }

        var propertyList = lineage.stream()
                .map(TypeInfo::elementInfo)
                .flatMap(Collection::stream)
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isPublic)
                .toList();

        Map<String, TypedElementInfo> propertyMap = new HashMap<>();

        propertyList.stream()
                .filter(m -> {
                    if (m.parameterArguments().size() != 1) {
                        confMethodBuilder.commentSkippedProperty(m, "doesn't have exactly one parameter");
                        return false;
                    }

                    for (var skip : skips) {
                        if (m.signature().text().matches(skip)) {
                            confMethodBuilder.commentSkippedProperty(m, "by pattern '" + skip + "'");
                            return false;
                        }
                    }
                    return true;
                })
                .forEach(m -> {
                    if (propertyMap.containsKey(m.signature().name())) {
                        if (overrideProps.containsKey(m.signature().name())) {
                            propertyMap.put(m.signature().name(), m);
                        }
                        confMethodBuilder.commentSkippedProperty(m, "property already exist -> {@code "
                                + propertyMap.get(m.signature().name()).signature() + "}");
                    } else {
                        propertyMap.put(m.signature().name(), m);
                    }
                });

        for (var m : propertyMap.values()) {
            var methodName = m.signature().name();
            var paramType = m.signature().parameterTypes().getFirst();
            var skipBuilderMapping = m.findAnnotation(LangchainTypes.MODEL_CUSTOM_BUILDER_MAPPING).isPresent();

            if (paramType.array() && !hasCollectionAlternative(m, propertyList)) {
                this.addArrayProperty(methodName, paramType, m, skipBuilderMapping);

            } else if (paramType.isList() || paramType.isSet() || paramType.isMap()) {
                this.addCollectionProperty(methodName, paramType, m, skipBuilderMapping);

            } else {
                this.addOptionalProperty(methodName, paramType, m, skipBuilderMapping);

            }
        }

        introspectOverrides();
        return this;
    }

    private void introspectOverrides() {
        for (var m : overrideProps.values()) {
            var propertyName = m.signature().name();
            var propertyType = m.signature().type();
            var nestedAnnotation = m.findAnnotation(LangchainTypes.MODEL_NESTED_CONFIG);
            boolean skipBuilderMapping = m.findAnnotation(LangchainTypes.MODEL_CUSTOM_BUILDER_MAPPING).isPresent();

            if (nestedAnnotation.isPresent()) {

                var nestedBluePrintBuilder = LlmNestedBlueprintBuilder.create(ctx, configRoot(), m, parentTypeInfo());

                if (propertyType.array()) {
                    this.addNestedBlueprintArrayProperty(m, nestedBluePrintBuilder, skipBuilderMapping);

                } else if (propertyType.isList() || propertyType.isSet() || propertyType.isMap()) {
                    this.addNestedBlueprintCollectionProperty(m, nestedBluePrintBuilder, skipBuilderMapping);

                } else {
                    this.addNestedBlueprintProperty(m, nestedBluePrintBuilder, skipBuilderMapping);
                }

            } else {

                if (propertyType.array() && !hasCollectionAlternative(m, new ArrayList<>(overrideProps.values()))) {
                    this.addArrayProperty(propertyName, propertyType, m, skipBuilderMapping);

                } else if (propertyType.isList() || propertyType.isSet() || propertyType.isMap()) {
                    this.addCollectionProperty(propertyName, propertyType, m, skipBuilderMapping);

                } else {
                    this.addOptionalProperty(propertyName, propertyType, m, skipBuilderMapping);

                }
            }
        }
    }

    void buildAndAdd() {
        if (!ModelConfigCodegen.GENERATED_CLASSES.contains(typeName())) {
            classModelBuilder().addMethod(confMethodBuilder.build());
            ctx.addGeneratedType(classModelBuilder().get().typeName(), classModelBuilder(), parentTypeInfo().typeName());
        }
    }

    /**
     * Return true if array property has Set or List alternative.
     *
     * @param m          array prop
     * @param methodList all the methods
     * @return true if does
     */
    protected boolean hasCollectionAlternative(TypedElementInfo m, List<TypedElementInfo> methodList) {
        return methodList.stream()
                .filter(i -> i.signature().name().equals(m.signature().name()))
                .filter(i -> i.parameterArguments().size() == 1)
                .anyMatch(i -> i.signature().parameterTypes().getFirst().isList() || i.signature().parameterTypes().getFirst()
                        .isSet());
    }
}
