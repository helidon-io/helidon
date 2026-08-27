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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
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
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_RUN_LEVEL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIED_INSTANCE;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;

class ModelFactoryCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(ModelConfigCodegen.class);
    private static final double DEFAULT_FACTORY_WEIGHT = Weighted.DEFAULT_WEIGHT - 2;
    private static final double LIFECYCLE_COORDINATOR_RUN_LEVEL = -Double.MAX_VALUE;
    private static final double LIFECYCLE_COORDINATOR_WEIGHT = Double.MAX_VALUE;

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

        var modelFactoryWeightAnnotation = modelFactoryWeightAnnotation(configType, modelAnnotation);

        var modelClassNamePrefix = modelAnnotation.typeValue().map(TypeName::className)
                .orElseThrow(() -> new CodegenException("Missing model class"));

        var factoryTypeName = TypeName.builder()
                .packageName(configType.typeName().packageName())
                .className(modelClassNamePrefix + "Factory")
                .build();
        var lifecycleStateType = TypeName.create(factoryTypeName.fqName() + ".LifecycleState");
        var lifecyclePhaseType = TypeName.create(factoryTypeName.fqName() + ".LifecyclePhase");
        var lifecycleCoordinatorType = TypeName.create(factoryTypeName.fqName() + "Lifecycle");

        var classModel = factoryClassModel(configType,
                                           modelType,
                                           modelClassNamePrefix,
                                           factoryTypeName,
                                           modelFactoryWeightAnnotation);

        addFields(classModel, modelType, lifecycleCoordinatorType, lifecycleStateType);

        classModel.addConstructor(Constructor.builder()
                                          .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                                          .description("Creates a new " + modelClassNamePrefix + "Factory.")
                                          .addContentLine("this.config = config;")
                                          .addContentLine("this.lifecycle = lifecycle;")
                                          .addContentLine("this.lifecycleChanged = lifecycleLock.newCondition();")
                                          .addContent("var modelNames = ")
                                          .addContent(constantClassTypeName)
                                          .addContent(".modelNames(config, ")
                                          .addContent(modelType)
                                          .addContent(".class, ")
                                          .addContent(modelClassNamePrefix + "Config.PROVIDER_KEY);")
                                          .addContentLine()
                                          .addContentLine("this.modelNames = modelNames.stream()")
                                          .increaseContentPadding()
                                          .addContentLine(".filter(this::modelEnabled)")
                                          .addContentLine(".toList();")
                                          .decreaseContentPadding()
                                          .addContentLine("this.serviceReferences = this.modelNames.stream()")
                                          .increaseContentPadding()
                                          .addContent(".<")
                                          .addContent(SERVICE_QUALIFIED_INSTANCE)
                                          .addContent("<")
                                          .addContent(modelType)
                                          .addContentLine(">>map(ModelQualifiedInstance::new)")
                                          .addContentLine(".toList();")
                                          .decreaseContentPadding()
                                          .addContent("this.lifecycleState = new ")
                                          .addContent(lifecycleStateType)
                                          .addContent("(")
                                          .addContent(lifecyclePhaseType)
                                          .addContent(".NEW, ")
                                          .addContent(Map.class)
                                          .addContent(".of(), ")
                                          .addContent(LIST)
                                          .addContentLine(".of(), null, null);")
                                          .addParameter(Parameter.builder()
                                                                .description("Configuration for the new model.")
                                                                .name("config")
                                                                .type(CONFIG)
                                                                .build())
                                          .addParameter(Parameter.builder()
                                                                .description("Lifecycle coordinator for the new model.")
                                                                .name("lifecycle")
                                                                .type(lifecycleCoordinatorType)
                                                                .build()));

        classModel.addMethod(servicesMethod(modelType, lifecycleStateType, lifecyclePhaseType));
        classModel.addMethod(resolvedModelsMethod(modelType, lifecycleStateType, lifecyclePhaseType));
        classModel.addMethod(initializeModelsMethod(modelType, lifecycleStateType, lifecyclePhaseType));
        classModel.addMethod(preDestroyMethod(lifecycleStateType, lifecyclePhaseType));
        classModel.addMethod(closeModelsMethod());
        classModel.addMethod(combineFailuresMethod());
        classModel.addMethod(throwCloseFailureMethod());
        classModel.addMethod(shouldCloseModelMethod());
        classModel.addMethod(addOwnedModelMethod());
        classModel.addMethod(modelEnabledMethod(modelType, constantClassTypeName));
        classModel.addMethod(resolveModelMethod(modelType));

        var modelNamePrefix = modelAnnotation.typeValue().map(TypeName::className)
                .orElseThrow(() -> new CodegenException("Missing model class"));

        var modelConfigTypeName = TypeName.builder()
                .packageName(configType.typeName().packageName())
                .className(modelNamePrefix + "Config")
                .build();

        classModel.addMethod(buildModelMethod(modelClassNamePrefix, modelType, constantClassTypeName));
        classModel.addMethod(createMethod(modelType, modelConfigTypeName));
        classModel.addInnerClass(lifecyclePhase());
        classModel.addInnerClass(lifecycleState(modelType, lifecyclePhaseType));
        classModel.addInnerClass(modelQualifiedInstance(modelType));
        roundContext.addGeneratedType(factoryTypeName, classModel, configType.typeName());
        roundContext.addGeneratedType(lifecycleCoordinatorType,
                                      lifecycleCoordinator(factoryTypeName, lifecycleCoordinatorType),
                                      configType.typeName());
    }

    private static void addFields(ClassModel.Builder classModel,
                                  TypeName modelType,
                                  TypeName lifecycleCoordinatorType,
                                  TypeName lifecycleStateType) {
        classModel.addField(Field.builder()
                                    .name("config")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(CONFIG)
                                    .build());
        classModel.addField(Field.builder()
                                    .name("lifecycle")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(lifecycleCoordinatorType)
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
                                    .name("serviceReferences")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(servicesType(modelType))
                                    .build());
        classModel.addField(Field.builder()
                                    .name("lifecycleLock")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(ReentrantLock.class)
                                    .addContent("new ")
                                    .addContent(ReentrantLock.class)
                                    .addContent("()")
                                    .build());
        classModel.addField(Field.builder()
                                    .name("lifecycleChanged")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isFinal(true)
                                    .type(Condition.class)
                                    .build());
        classModel.addField(Field.builder()
                                    .name("lifecycleState")
                                    .accessModifier(AccessModifier.PRIVATE)
                                    .isVolatile(true)
                                    .type(lifecycleStateType)
                                    .build());
    }

    private static Annotation modelFactoryWeightAnnotation(TypeInfo configType, Annotation modelAnnotation) {
        return modelAnnotation.doubleValue("weight")
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
    }

    private static ClassModel.Builder factoryClassModel(TypeInfo configType,
                                                        TypeName modelType,
                                                        String modelClassNamePrefix,
                                                        TypeName factoryTypeName,
                                                        Annotation modelFactoryWeightAnnotation) {
        return ClassModel.builder()
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
                .addInterface(TypeName.builder(SVC_SERVICES_FACTORY).addTypeArgument(modelType).build());
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
    private static Method servicesMethod(TypeName modelType,
                                         TypeName lifecycleStateType,
                                         TypeName lifecyclePhaseType) {
        return Method.builder()
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(PUBLIC)
                .name("services")
                .returnType(servicesType(modelType))
                .addContentLine("lifecycle.register(this);")
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("var state = lifecycleState;")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYING || state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".DESTROYED) {")
                .increaseContentPadding()
                .addContent("return ")
                .addContent(LIST)
                .addContentLine(".of();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".CLEANUP_FAILED) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Cannot initialize LangChain4j models after cleanup failed.")
                .addContentLine(", state.failure());")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return serviceReferences;")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .build();
    }

    private static Method resolvedModelsMethod(TypeName modelType,
                                               TypeName lifecycleStateType,
                                               TypeName lifecyclePhaseType) {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .name("resolvedModels")
                .returnType(modelsType(modelType))
                .addContentLine("var state = lifecycleState;")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".READY) {")
                .increaseContentPadding()
                .addContentLine("return state.models();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("while (true) {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("state = lifecycleState;")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".READY) {")
                .increaseContentPadding()
                .addContentLine("return state.models();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYING || state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".DESTROYED) {")
                .increaseContentPadding()
                .addContent("return ")
                .addContent(Map.class)
                .addContentLine(".of();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".CLEANUP_FAILED) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Cannot initialize LangChain4j models after cleanup failed.")
                .addContentLine(", state.failure());")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".INITIALIZING) {")
                .increaseContentPadding()
                .addContent("if (state.owner() == ")
                .addContent(Thread.class)
                .addContentLine(".currentThread()) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Recursive LangChain4j model factory initialization.")
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("lifecycleChanged.await();")
                .decreaseContentPadding()
                .addContent("} catch (")
                .addContent(InterruptedException.class)
                .addContentLine(" e) {")
                .increaseContentPadding()
                .addContent(Thread.class)
                .addContentLine(".currentThread().interrupt();")
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Interrupted while waiting for LangChain4j model factory initialization.")
                .addContentLine(", e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("continue;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".INITIALIZING, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContent(".of(), ")
                .addContent(Thread.class)
                .addContentLine(".currentThread(), null);")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return initializeModels();")
                .decreaseContentPadding()
                .addContentLine("}")
                .build();
    }

    private static Method initializeModelsMethod(TypeName modelType,
                                                 TypeName lifecycleStateType,
                                                 TypeName lifecyclePhaseType) {
        var builder = Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .name("initializeModels")
                .returnType(modelsType(modelType))
                .addContent("var createdModels = new ")
                .addContent(HashMap.class)
                .addContent("<")
                .addContent(STRING)
                .addContent(", ")
                .addContent(modelType)
                .addContentLine(">();")
                .addContent("var ownedModels = new ")
                .addContent(ArrayList.class)
                .addContent("<")
                .addContent(AutoCloseable.class)
                .addContentLine(">();")
                .addContent(lifecycleStateType)
                .addContentLine(" completedState;")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("for (var name : modelNames) {")
                .increaseContentPadding()
                .addContentLine("buildModel(name, config, ownedModels)")
                .increaseContentPadding()
                .addContentLine(".ifPresent(model -> createdModels.put(name, model));")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("completedState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".READY, ")
                .addContent(Collections.class)
                .addContent(".unmodifiableMap(new ")
                .addContent(HashMap.class)
                .addContent("<>(createdModels)), ")
                .addContent(LIST)
                .addContentLine(".copyOf(ownedModels), null, null);")
                .decreaseContentPadding()
                .addContent("} catch (")
                .addContent(RuntimeException.class)
                .addContent(" | ")
                .addContent(Error.class)
                .addContentLine(" e) {")
                .increaseContentPadding()
                .addContentLine("var cleanupFailure = closeModels(ownedModels);")
                .addContentLine("if (cleanupFailure != null && cleanupFailure != e) {")
                .increaseContentPadding()
                .addContentLine("e.addSuppressed(cleanupFailure);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("if (cleanupFailure == null) {")
                .increaseContentPadding()
                .addContent("var phase = lifecycleState.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContent(".INITIALIZING ? ")
                .addContent(lifecyclePhaseType)
                .addContent(".NEW : ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".DESTROYED;")
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(phase, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, null);")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".CLEANUP_FAILED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, cleanupFailure);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("lifecycleChanged.signalAll();")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("throw e;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContent("if (lifecycleState.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".INITIALIZING) {")
                .increaseContentPadding()
                .addContentLine("lifecycleState = completedState;")
                .addContentLine("lifecycleChanged.signalAll();")
                .addContentLine("return completedState.models();")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}");
        addInitializationShutdownCleanup(builder, lifecycleStateType, lifecyclePhaseType);
        return builder.build();
    }

    private static void addInitializationShutdownCleanup(Method.Builder builder,
                                                         TypeName lifecycleStateType,
                                                         TypeName lifecyclePhaseType) {
        builder
                .addContentLine("var cleanupFailure = closeModels(completedState.ownedModels());")
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("if (cleanupFailure == null) {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, null);")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".CLEANUP_FAILED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, cleanupFailure);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("lifecycleChanged.signalAll();")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("throwCloseFailure(cleanupFailure);")
                .addContent("return ")
                .addContent(Map.class)
                .addContentLine(".of();");
    }

    private static Method preDestroyMethod(TypeName lifecycleStateType, TypeName lifecyclePhaseType) {
        var builder = Method.builder()
                .accessModifier(PACKAGE_PRIVATE)
                .name("preDestroy")
                .addContent(closeablesType())
                .addContentLine(" modelsToClose = null;")
                .addContentLine("while (true) {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("var state = lifecycleState;")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".DESTROYED) {")
                .increaseContentPadding()
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".NEW) {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, null);")
                .addContentLine("lifecycleChanged.signalAll();")
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".CLEANUP_FAILED) {")
                .increaseContentPadding()
                .addContentLine("throwCloseFailure(state.failure());")
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".READY) {")
                .increaseContentPadding()
                .addContentLine("modelsToClose = state.ownedModels();")
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYING, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent("modelsToClose, ")
                .addContent(Thread.class)
                .addContentLine(".currentThread(), null);")
                .addContentLine("break;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (state.phase() == ")
                .addContent(lifecyclePhaseType)
                .addContentLine(".INITIALIZING) {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYING, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), state.owner(), null);")
                .addContentLine("lifecycleChanged.signalAll();")
                .addContent("if (state.owner() == ")
                .addContent(Thread.class)
                .addContentLine(".currentThread()) {")
                .increaseContentPadding()
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContent("} else if (state.owner() == ")
                .addContent(Thread.class)
                .addContentLine(".currentThread()) {")
                .increaseContentPadding()
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("lifecycleChanged.await();")
                .decreaseContentPadding()
                .addContent("} catch (")
                .addContent(InterruptedException.class)
                .addContentLine(" e) {")
                .increaseContentPadding()
                .addContent(Thread.class)
                .addContentLine(".currentThread().interrupt();")
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("Interrupted while waiting for LangChain4j model factory shutdown.")
                .addContentLine(", e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}");
        addShutdownCleanup(builder, lifecycleStateType, lifecyclePhaseType);
        return builder.build();
    }

    private static void addShutdownCleanup(Method.Builder builder,
                                           TypeName lifecycleStateType,
                                           TypeName lifecyclePhaseType) {
        builder
                .addContentLine("var cleanupFailure = closeModels(modelsToClose);")
                .addContentLine("lifecycleLock.lock();")
                .addContentLine("try {")
                .increaseContentPadding()
                .addContentLine("if (cleanupFailure == null) {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".DESTROYED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, null);")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .increaseContentPadding()
                .addContent("lifecycleState = new ")
                .addContent(lifecycleStateType)
                .addContent("(")
                .addContent(lifecyclePhaseType)
                .addContent(".CLEANUP_FAILED, ")
                .addContent(Map.class)
                .addContent(".of(), ")
                .addContent(LIST)
                .addContentLine(".of(), null, cleanupFailure);")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("lifecycleChanged.signalAll();")
                .decreaseContentPadding()
                .addContentLine("} finally {")
                .increaseContentPadding()
                .addContentLine("lifecycleLock.unlock();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (cleanupFailure != null) {")
                .increaseContentPadding()
                .addContentLine("throwCloseFailure(cleanupFailure);")
                .decreaseContentPadding()
                .addContentLine("}");
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
                .addContent("var closed = ")
                .addContent(Collections.class)
                .addContent(".newSetFromMap(new ")
                .addContent(IdentityHashMap.class)
                .addContent("<")
                .addContent(AutoCloseable.class)
                .addContent(", ")
                .addContent(Boolean.class)
                .addContentLine(">());")
                .addContentLine("Throwable failure = null;")
                .addContentLine("boolean interrupted = false;")
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
                .addContent("if (e instanceof ")
                .addContent(InterruptedException.class)
                .addContentLine(") {")
                .increaseContentPadding()
                .addContentLine("interrupted = true;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("failure = combineFailures(failure, e);")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (interrupted) {")
                .increaseContentPadding()
                .addContent(Thread.class)
                .addContentLine(".currentThread().interrupt();")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return failure;")
                .build();
    }

    private static Method combineFailuresMethod() {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .name("combineFailures")
                .returnType(TypeName.create(Throwable.class))
                .addParameter(Parameter.builder()
                                      .type(Throwable.class)
                                      .name("previousFailure")
                                      .build())
                .addParameter(Parameter.builder()
                                      .type(Throwable.class)
                                      .name("newFailure")
                                      .build())
                .addContentLine("if (previousFailure == null) {")
                .increaseContentPadding()
                .addContentLine("return newFailure;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("if (newFailure == null || previousFailure == newFailure) {")
                .increaseContentPadding()
                .addContentLine("return previousFailure;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContent("if (newFailure instanceof ")
                .addContent(Error.class)
                .addContent(" && !(previousFailure instanceof ")
                .addContent(Error.class)
                .addContentLine(")) {")
                .increaseContentPadding()
                .addContentLine("newFailure.addSuppressed(previousFailure);")
                .addContentLine("return newFailure;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("previousFailure.addSuppressed(newFailure);")
                .addContentLine("return previousFailure;")
                .build();
    }

    private static Method throwCloseFailureMethod() {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .name("throwCloseFailure")
                .addParameter(Parameter.builder()
                                      .type(Throwable.class)
                                      .name("failure")
                                      .build())
                .addContentLine("if (failure == null) {")
                .increaseContentPadding()
                .addContentLine("return;")
                .decreaseContentPadding()
                .addContentLine("}")
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

    private static Method modelEnabledMethod(TypeName modelType, TypeName constantsClassTypeName) {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .name("modelEnabled")
                .returnType(TypeNames.PRIMITIVE_BOOLEAN)
                .addParameter(Parameter.builder()
                                      .type(STRING)
                                      .name("modelName")
                                      .build())
                .addContent("return ")
                .addContent(constantsClassTypeName)
                .addContent(".create(config, ")
                .addContent(modelType)
                .addContentLine(".class, modelName)")
                .increaseContentPadding()
                .addContentLine(".get(\"enabled\")")
                .addContentLine(".asBoolean()")
                .addContentLine(".orElse(true);")
                .build();
    }

    private static Method resolveModelMethod(TypeName modelType) {
        return Method.builder()
                .accessModifier(AccessModifier.PRIVATE)
                .name("resolveModel")
                .returnType(modelType)
                .addParameter(Parameter.builder()
                                      .type(STRING)
                                      .name("modelName")
                                      .build())
                .addContentLine("var model = resolvedModels().get(modelName);")
                .addContentLine("if (model == null) {")
                .increaseContentPadding()
                .addContent("throw new ")
                .addContent(IllegalStateException.class)
                .addContent("(")
                .addContentLiteral("No initialized LangChain4j model named '")
                .addContent(" + modelName + ")
                .addContentLiteral("'.")
                .addContentLine(");")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return model;")
                .build();
    }

    private static TypeName modelsType(TypeName modelType) {
        return TypeName.builder(TypeName.create(Map.class))
                .addTypeArgument(STRING)
                .addTypeArgument(modelType)
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

    private static TypeName qualifiersType() {
        return TypeName.builder(TypeName.create(Set.class))
                .addTypeArgument(SERVICE_QUALIFIER)
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

    private static InnerClass modelQualifiedInstance(TypeName modelType) {
        return InnerClass.builder()
                .name("ModelQualifiedInstance")
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .addInterface(TypeName.builder(SERVICE_QUALIFIED_INSTANCE)
                                      .addTypeArgument(modelType)
                                      .build())
                .addField(Field.builder()
                                  .name("modelName")
                                  .accessModifier(AccessModifier.PRIVATE)
                                  .isFinal(true)
                                  .type(STRING)
                                  .build())
                .addField(Field.builder()
                                  .name("qualifiers")
                                  .accessModifier(AccessModifier.PRIVATE)
                                  .isFinal(true)
                                  .type(qualifiersType())
                                  .build())
                .addConstructor(Constructor.builder()
                                        .accessModifier(AccessModifier.PRIVATE)
                                        .addParameter(Parameter.builder()
                                                              .type(STRING)
                                                              .name("modelName")
                                                              .build())
                                        .addContentLine("this.modelName = modelName;")
                                        .addContent("this.qualifiers = ")
                                        .addContent(Set.class)
                                        .addContent(".of(")
                                        .addContent(SERVICE_QUALIFIER)
                                        .addContentLine(".createNamed(modelName));"))
                .addMethod(Method.builder()
                                   .addAnnotation(Annotations.OVERRIDE)
                                   .accessModifier(PUBLIC)
                                   .name("get")
                                   .returnType(modelType)
                                   .addContentLine("return resolveModel(modelName);"))
                .addMethod(Method.builder()
                                   .addAnnotation(Annotations.OVERRIDE)
                                   .accessModifier(PUBLIC)
                                   .name("qualifiers")
                                   .returnType(qualifiersType())
                                   .addContentLine("return qualifiers;"))
                .build();
    }

    private static InnerClass lifecyclePhase() {
        return InnerClass.builder()
                .name("LifecyclePhase")
                .accessModifier(AccessModifier.PRIVATE)
                .classType(ElementKind.ENUM)
                .addEnumConstant(it -> it.name("NEW"))
                .addEnumConstant(it -> it.name("INITIALIZING"))
                .addEnumConstant(it -> it.name("READY"))
                .addEnumConstant(it -> it.name("CLEANUP_FAILED"))
                .addEnumConstant(it -> it.name("DESTROYING"))
                .addEnumConstant(it -> it.name("DESTROYED"))
                .build();
    }

    private static InnerClass lifecycleState(TypeName modelType, TypeName lifecyclePhaseType) {
        return InnerClass.builder()
                .name("LifecycleState")
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .classType(ElementKind.RECORD)
                .sortFields(false)
                .addField(it -> it.name("phase")
                        .type(lifecyclePhaseType))
                .addField(it -> it.name("models")
                        .type(modelsType(modelType)))
                .addField(it -> it.name("ownedModels")
                        .type(closeablesType()))
                .addField(it -> it.name("owner")
                        .type(Thread.class))
                .addField(it -> it.name("failure")
                        .type(Throwable.class))
                .build();
    }

    private static ClassModel.Builder lifecycleCoordinator(TypeName factoryTypeName, TypeName lifecycleCoordinatorType) {
        var factoryReferenceType = TypeName.builder(TypeName.create(AtomicReference.class))
                .addTypeArgument(factoryTypeName)
                .build();
        return ClassModel.builder()
                .classType(ElementKind.CLASS)
                .type(lifecycleCoordinatorType)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 factoryTypeName,
                                                 lifecycleCoordinatorType))
                .addDescriptionLine("Coordinates shutdown of the " + factoryTypeName.className() + ".")
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               factoryTypeName,
                                                               lifecycleCoordinatorType,
                                                               "1",
                                                               ""))
                .accessModifier(PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_SINGLETON))
                .addAnnotation(Annotation.builder()
                                       .typeName(SERVICE_ANNOTATION_RUN_LEVEL)
                                       .putProperty("value",
                                                    AnnotationProperty.create(LIFECYCLE_COORDINATOR_RUN_LEVEL))
                                       .build())
                .addAnnotation(Annotation.builder()
                                       .typeName(WEIGHT)
                                       .putProperty("value",
                                                    AnnotationProperty.create(LIFECYCLE_COORDINATOR_WEIGHT,
                                                                              TypeNames.BOXED_DOUBLE,
                                                                              "MAX_VALUE"))
                                       .build())
                .addField(Field.builder()
                                  .name("factory")
                                  .accessModifier(AccessModifier.PRIVATE)
                                  .isFinal(true)
                                  .type(factoryReferenceType)
                                  .addContent("new ")
                                  .addContent(AtomicReference.class)
                                  .addContent("<>()")
                                  .build())
                .addField(Field.builder()
                                  .name("destroyed")
                                  .accessModifier(AccessModifier.PRIVATE)
                                  .isFinal(true)
                                  .type(AtomicBoolean.class)
                                  .addContent("new ")
                                  .addContent(AtomicBoolean.class)
                                  .addContent("()")
                                  .build())
                .addMethod(Method.builder()
                                   .accessModifier(PACKAGE_PRIVATE)
                                   .name("register")
                                   .addParameter(Parameter.builder()
                                                         .name("factory")
                                                         .type(factoryTypeName)
                                                         .build())
                                   .addContentLine("var existing = this.factory.compareAndExchange(null, factory);")
                                   .addContentLine("if (existing != null && existing != factory) {")
                                   .increaseContentPadding()
                                   .addContent("throw new ")
                                   .addContent(IllegalStateException.class)
                                   .addContent("(")
                                   .addContentLiteral("A different LangChain4j model factory is already registered.")
                                   .addContentLine(");")
                                   .decreaseContentPadding()
                                   .addContentLine("}")
                                   .addContentLine("if (destroyed.get()) {")
                                   .increaseContentPadding()
                                   .addContentLine("factory.preDestroy();")
                                   .decreaseContentPadding()
                                   .addContentLine("}")
                                   .build())
                .addMethod(Method.builder()
                                   .addAnnotation(Annotation.create(SERVICE_ANNOTATION_PRE_DESTROY))
                                   .accessModifier(PACKAGE_PRIVATE)
                                   .name("preDestroy")
                                   .addContentLine("destroyed.set(true);")
                                   .addContentLine("var factory = this.factory.get();")
                                   .addContentLine("if (factory != null) {")
                                   .increaseContentPadding()
                                   .addContentLine("factory.preDestroy();")
                                   .decreaseContentPadding()
                                   .addContentLine("}")
                                   .build());
    }
}
