/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.Returns;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Weighted;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.common.types.AccessModifier.PACKAGE_PRIVATE;
import static io.helidon.common.types.AccessModifier.PROTECTED;
import static io.helidon.common.types.AccessModifier.PUBLIC;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.OPTIONAL;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.common.types.TypeNames.WEIGHT;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MODEL_CONFIGS_TYPE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MODEL_CONFIG_TYPE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MODEL_LIFECYCLE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.SVC_SERVICES_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PRE_DESTROY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIED_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;

class ModelFactoryCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(ModelConfigCodegen.class);
    private static final double DEFAULT_FACTORY_WEIGHT = Weighted.DEFAULT_WEIGHT - 2;

    @Override
    public void process(RoundContext roundContext) {
        var types = roundContext.types();
        for (TypeInfo type : types) {
            var providerClassPrefix = ModelCodegenHelper.providerFromClassName(type);
            var providerKey = ModelCodegenHelper.camelToKebabCase(providerClassPrefix);
            var constantClassTypeName =
                    ConstantsBuilder.create(roundContext, providerClassPrefix + "Constants", type, providerKey);
            type.findAnnotation(MODEL_CONFIGS_TYPE)
                    .flatMap(Annotation::annotationValues)
                    .or(() -> type.findAnnotation(MODEL_CONFIG_TYPE).map(List::of))
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(modelAnnotation ->
                                     process(roundContext, type, providerKey, modelAnnotation, constantClassTypeName));
        }
    }

    private void process(RoundContext roundContext,
                         TypeInfo configType,
                         String providerKey,
                         Annotation modelAnnotation,
                         TypeName constantClassTypeName) {

        var modelType = modelAnnotation.typeValue().orElseThrow();

        var modelFactoryWeightAnnotation = modelAnnotation.doubleValue("weight")
                .filter(w -> w != Weighted.DEFAULT_WEIGHT)
                .map(w -> Annotation.builder()
                        .typeName(WEIGHT)
                        .addProperties(Map.of("value", AnnotationProperty.create(w)))
                        .build())
                .or(() -> configType.elementInfo().stream()
                        .filter(e -> e.kind().equals(ElementKind.FIELD))
                        .filter(e -> e.typeName().equals(TypeNames.PRIMITIVE_DOUBLE))
                        .filter(e -> e.hasAnnotation(LangchainTypes.MODEL_DEFAULT_WEIGHT))
                        .findFirst()
                        .map(e -> Annotation.builder()
                                .typeName(WEIGHT)
                                .putProperty("value", AnnotationProperty.create("weight",
                                                                                configType.typeName(),
                                                                                e.elementName()))
                                .build())
                ).orElseGet(() -> Annotation.builder()
                        .typeName(WEIGHT)
                        .addProperties(Map.of("value", AnnotationProperty.create(DEFAULT_FACTORY_WEIGHT)))
                        .build());

        var modelClassNamePrefix = modelAnnotation.typeValue().map(TypeName::className)
                .orElseThrow(() -> new CodegenException("Missing model class"));

        var factoryTypeName = TypeName.builder()
                .packageName(configType.typeName().packageName())
                .className(modelClassNamePrefix + "Factory")
                .build();

        var superTypeName = TypeName.builder(SVC_SERVICES_FACTORY).addTypeArgument(modelType);

        var classModel = ClassModel.builder()
                .classType(ElementKind.CLASS)
                .type(factoryTypeName)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 configType.typeName(),
                                                 factoryTypeName))
                .addDescriptionLine("Service factory for the " + modelClassNamePrefix + ".")
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               configType.typeName(),
                                                               factoryTypeName,
                                                               "1",
                                                               ""))
                .accessModifier(PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON))
                .addAnnotation(Annotation.builder()
                                       .typeName(SERVICE_ANNOTATION_NAMED)
                                       .putProperty("value", AnnotationProperty.create("value",
                                                                                       SERVICE_ANNOTATION_NAMED,
                                                                                       "WILDCARD_NAME"))
                                       .build())
                .addAnnotation(modelFactoryWeightAnnotation)
                .addInterface(superTypeName.build());

        classModel.addField(Field.builder()
                                    .name("config")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(CONFIG)
                                    .build());

        classModel.addField(Field.builder()
                                    .name("modelNames")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(TypeName.builder(LIST)
                                                  .addTypeArgument(STRING)
                                                  .build())
                                    .build());

        classModel.addField(Field.builder()
                                    .name("services")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .type(servicesType(modelType))
                                    .build());

        classModel.addField(Field.builder()
                                    .name("ownedModels")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .type(closeablesType())
                                    .build());

        classModel.addConstructor(Constructor.builder()
                                          .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                                          .description("Creates a new " + modelClassNamePrefix + "Factory.")
                                          .addContentLine("this.config = config;")
                                          .addContent("this.modelNames = ")
                                          .addContent(constantClassTypeName)
                                          .addContent(".modelNames(config, ")
                                          .addContent(modelType)
                                          .addContent(".class, ")
                                          .addContent(modelClassNamePrefix + "Config.PROVIDER_KEY);")
                                          .addParameter(Parameter.builder()
                                                                .description("Configuration for the new model.")
                                                                .name("config")
                                                                .type(CONFIG)
                                                                .build()));

        classModel.addMethod(servicesMethod(modelType));
        classModel.addMethod(preDestroyMethod());
        classModel.addMethod(closeModelsMethod());
        classModel.addMethod(shouldCloseModelMethod());
        classModel.addMethod(addOwnedModelMethod());

        var modelNamePrefix = modelAnnotation.typeValue().map(TypeName::className)
                .orElseThrow(() -> new CodegenException("Missing model class"));

        var modelConfigTypeName = TypeName.builder()
                .packageName(configType.typeName().packageName())
                .className(modelNamePrefix + "Config")
                .build();

        classModel.addMethod(buildModelMethod(modelClassNamePrefix, modelType, constantClassTypeName));
        classModel.addMethod(createMethod(modelType, modelConfigTypeName));
        roundContext.addGeneratedType(factoryTypeName, classModel, configType.typeName());
    }

    /*
    @Override
    public List<Service.QualifiedInstance<OciGenAiChatModel>> services() {
        var modelOptional = model().get();
        if (modelOptional.isEmpty()) {
                return List.of();
        }
        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
            Service.QualifiedInstance.create(theModel, OciGenAi.QUALIFIER));
    }
     */
    private static Method servicesMethod(TypeName modelType) {
        return Method.builder()
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(PUBLIC)
                .name("services")
                .returnType(servicesType(modelType))
                .addContentLine("synchronized (this) {")
                .increaseContentPadding()
                .addContentLine("if (services == null) {")
                .increaseContentPadding()
                .addContent("var createdServices = new ")
                .addContent(ArrayList.class)
                .addContent("<")
                .addContent(SERVICE_QUALIFIED_INSTANCE)
                .addContent("<")
                .addContent(modelType)
                .addContentLine(">>();")
                .addContent("var createdModels = new ")
                .addContent(ArrayList.class)
                .addContent("<")
                .addContent(AutoCloseable.class)
                .addContentLine(">();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("for (var name : modelNames) {")
                .increaseContentPadding()
                .addContentLine("buildModel(name, config, createdModels)")
                .increaseContentPadding()
                .addContent(".map(model -> ")
                .addContent(SERVICE_QUALIFIED_INSTANCE)
                .addContentLine()
                .increaseContentPadding()
                .addContent(".create(model, ")
                .addContent(SERVICE_QUALIFIER)
                .addContentLine(".createNamed(name)))")
                .decreaseContentPadding()
                .addContentLine(".ifPresent(createdServices::add);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("var completedServices = ")
                .addContent(LIST)
                .addContentLine(".copyOf(createdServices);")
                .addContent("var completedModels = ")
                .addContent(LIST)
                .addContentLine(".copyOf(createdModels);")
                .addContentLine("services = completedServices;")
                .addContentLine("ownedModels = completedModels;")
                .decreaseContentPadding()
                .addContent("} catch (")
                .addContent(RuntimeException.class)
                .addContent(" | ")
                .addContent(Error.class)
                .addContentLine(" e) {")
                .increaseContentPadding()
                .addContentLine("closeModels(createdModels, e);")
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return services;")
                .decreaseContentPadding()
                .addContentLine("}")
                .build();
    }

    private static Method preDestroyMethod() {
        return Method.builder()
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_PRE_DESTROY))
                .accessModifier(PACKAGE_PRIVATE)
                .name("preDestroy")
                .addContent(closeablesType())
                .addContentLine(" modelsToClose;")
                .addContentLine("synchronized (this) {")
                .increaseContentPadding()
                .addContentLine("modelsToClose = ownedModels;")
                .addContent("services = ")
                .addContent(LIST)
                .addContentLine(".of();")
                .addContent("ownedModels = ")
                .addContent(LIST)
                .addContentLine(".of();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (modelsToClose == null) {")
                .increaseContentPadding()
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("var failure = closeModels(modelsToClose, null);")
                .addContentLine("if (failure != null) {")
                .increaseContentPadding()
                .addContent("if (failure instanceof ")
                .addContent(Error.class)
                .addContentLine(" error) {")
                .increaseContentPadding()
                .addContentLine("throw error;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Failed to close LangChain4j model instances.")
                .addContentLine(", failure);")
                .decreaseContentPadding()
                .addContentLine("}")
                .build();
    }

    private static Method closeModelsMethod() {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .name("closeModels")
                .returnType(TypeName.create(Throwable.class))
                .addParameter(Parameter.builder()
                                      .type(closeablesType())
                                      .name("models")
                                      .build())
                .addParameter(Parameter.builder()
                                      .type(Throwable.class)
                                      .name("failure")
                                      .build())
                .addContent("var closed = ")
                .addContent(Collections.class)
                .addContent(".newSetFromMap(new ")
                .addContent(IdentityHashMap.class)
                .addContent("<")
                .addContent(AutoCloseable.class)
                .addContent(", ")
                .addContent(Boolean.class)
                .addContentLine(">());")
                .addContentLine("for (var model : models) {")
                .increaseContentPadding()
                .addContentLine("if (closed.add(model)) {")
                .increaseContentPadding()
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("model.close();")
                .decreaseContentPadding()
                .addContent("} catch (")
                .addContent(Throwable.class)
                .addContentLine(" e) {")
                .increaseContentPadding()
                .addContentLine("if (failure == null) {")
                .increaseContentPadding()
                .addContentLine("failure = e;")
                .decreaseContentPadding()
                .addContentLine("} else if (failure != e) {")
                .increaseContentPadding()
                .addContentLine("failure.addSuppressed(e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return failure;")
                .build();
    }

    private static Method shouldCloseModelMethod() {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .name("shouldCloseModel")
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .addParameter(Parameter.builder()
                                      .type(TypeNames.OBJECT)
                                      .name("modelConfig")
                                      .build())
                .addContent("return !(modelConfig instanceof ")
                .addContent(MODEL_LIFECYCLE)
                .addContentLine(" lifecycle) || lifecycle.closeModelOnShutdown();")
                .build();
    }

    private static Method addOwnedModelMethod() {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .name("addOwnedModel")
                .addParameter(Parameter.builder()
                                      .type(TypeNames.OBJECT)
                                      .name("model")
                                      .build())
                .addParameter(Parameter.builder()
                                      .type(closeablesType())
                                      .name("ownedModels")
                                      .build())
                .addContent("if (model instanceof ")
                .addContent(AutoCloseable.class)
                .addContentLine(" closeable) {")
                .increaseContentPadding()
                .addContentLine("ownedModels.add(closeable);")
                .decreaseContentPadding()
                .addContentLine("}")
                .build();
    }

    private static TypeName servicesType(TypeName modelType) {
        return TypeName.builder(LIST)
                .addTypeArgument(TypeName.builder(SERVICE_QUALIFIED_INSTANCE)
                                         .addTypeArgument(modelType)
                                         .build())
                .build();
    }

    private static TypeName closeablesType() {
        return TypeName.builder(LIST)
                .addTypeArgument(TypeName.create(AutoCloseable.class))
                .build();
    }

    /*
    protected static Optional<OciGenAiChatModel> buildModel(OciGenAiChatModelConfig.Builder configBuilder) {
        if (!configBuilder.enabled()) {
                return Optional.empty();
        }
        return Optional.of(create(configBuilder.build()));
    }
     */
    private static Method buildModelMethod(String modelClassNamePrefix, TypeName modelType, TypeName constantsClassTypeName) {
        return Method.builder()
                .accessModifier(PROTECTED)
                .name("buildModel")
                .description("Builds a new model configured with the given configuration builder.")
                .addParameter(Parameter.builder()
                                      .type(STRING)
                                      .description("Model name.")
                                      .name("modelName")
                                      .build())
                .addParameter(Parameter.builder()
                                      .type(CONFIG)
                                      .description("Configuration for the new model.")
                                      .name("config")
                                      .build())
                .addParameter(Parameter.builder()
                                      .type(closeablesType())
                                      .description("Models owned by this factory.")
                                      .name("ownedModels")
                                      .build())
                .returnType(Returns.builder()
                                    .description("New model configured with the given configuration builder.")
                                    .type(TypeName.builder(OPTIONAL).addTypeArgument(modelType).build())
                                    .build())
                .addContent("var mergedConfig = ")
                .addContent(constantsClassTypeName)
                .addContent(".create(config, ")
                .addContent(modelType)
                .addContentLine(".class, modelName);")
                .addContent("var configBuilder = ")
                .addContentLine(modelClassNamePrefix + "Config.builder()")
                .addContentLine(".config(mergedConfig);")
                .addContentLine()
                .addContentLine("if (!configBuilder.enabled()) {")
                .increaseContentPadding()
                .addContent("return ").addContent(OPTIONAL).addContentLine(".empty();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("var modelConfig = configBuilder.build();")
                .addContentLine("boolean closeModel = shouldCloseModel(modelConfig);")
                .addContentLine("var model = create(modelConfig);")
                .addContentLine("if (closeModel) {")
                .increaseContentPadding()
                .addContentLine("addOwnedModel(model, ownedModels);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("return ").addContent(OPTIONAL)
                .addContentLine(".of(model);")
                .build();
    }

    /*
      static OciGenAiChatModel create(OciGenAiChatModelConfig config) {
          if (!config.enabled()) {
                  throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
          }
          return config.configuredBuilder().build();
      }
    */
    private static Method createMethod(TypeName modelType, TypeName modelConfigTypeName) {
        return Method.builder()
                .name("create")
                .accessModifier(PACKAGE_PRIVATE)
                .isStatic(true)
                .description("Creates a new model configured with the given configuration.")
                .returnType(Returns.builder()
                                    .description("New model configured with the given configuration.")
                                    .type(modelType)
                                    .build())
                .addParameter(Parameter.builder()
                                      .name("config")
                                      .description("Configuration for the new model.")
                                      .type(modelConfigTypeName)
                                      .build())
                .addContentLine("if (!config.enabled()) {")
                .increaseContentPadding()
                .addContent("throw new ").addContent(IllegalStateException.class).addContent("(")
                .addContentLiteral("Cannot create a model when the configuration is disabled.").addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return config.configuredBuilder().build();")
                .build();
    }
}
