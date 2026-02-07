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

package io.helidon.integrations.langchain4j.codegen;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Parameter;
import io.helidon.codegen.classmodel.Returns;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.common.types.AccessModifier.PACKAGE_PRIVATE;
import static io.helidon.common.types.AccessModifier.PRIVATE;
import static io.helidon.common.types.AccessModifier.PUBLIC;
import static io.helidon.common.types.TypeNames.CLASS_WILDCARD;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.STRING;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.CONFIG;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_EMBEDDING_STORE;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.MERGED_CONFIG;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;

/**
 * Code generator for provider-specific constants used by Helidon's LangChain4j integration.
 * <p>
 * Generates a package-private constants class in the same package as the {@code triggerType} which contains:
 * <ul>
 *     <li>Root configuration keys (e.g., {@code langchain4j}, {@code langchain4j.models}, {@code langchain4j.providers})</li>
 *     <li>A provider {@code QUALIFIER} for looking up named services</li>
 *     <li>Helper methods to resolve a configuration category, list model names, and create merged model/provider configs</li>
 * </ul>
 * The generated constants type is consumed by other generated sources to consistently locate and merge configuration.
 */
class ConstantsBuilder {
    private static final TypeName GENERATOR = TypeName.create(ConstantsBuilder.class);
    static final TypeName INNER_CONFIG_CATEGORY_ENUM = TypeName.create("ConfigCategory");

    private ConstantsBuilder() {
    }

    /**
     * Creates and registers a generated constants type for the specified LangChain4j provider.
     * <p>
     * The generated type contains constant configuration keys and helper methods used by other generated
     * sources to locate and merge provider and model configuration.
     *
     * @param context     the current code generation round context used to register the generated type
     * @param className   the generated constants class simple name
     * @param triggerType the type that triggered generation (used for package/attribution)
     * @param providerKey the provider key used to namespace provider-specific constants and defaults
     * @return the {@link TypeName} of the generated constants type
     */
    static TypeName create(RoundContext context, String className, TypeInfo triggerType, String providerKey) {
        var classTypeName = TypeName.builder()
                .packageName(triggerType.typeName().packageName())
                .className(className)
                .build();

        var classModel = ClassModel.builder()
                .sortStaticFields(false)
                .classType(ElementKind.CLASS)
                .type(classTypeName)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 triggerType.typeName(),
                                                 classTypeName))
                .addDescriptionLine("Constants for '" + providerKey + "' Lc4j provider.")
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               triggerType.typeName(),
                                                               classTypeName,
                                                               "1",
                                                               ""));

        classModel.addField(Field.builder()
                                    .name("QUALIFIER")
                                    .isStatic(true)
                                    .isFinal(true)
                                    .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                                    .type(SERVICE_QUALIFIER)
                                    .addContent(SERVICE_QUALIFIER)
                                    .addContent(".createNamed(\"")
                                    .addContent(providerKey)
                                    .addContent("\")")
                                    .build());

        classModel.addField(f -> f
                .name("LC4J_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContentLiteral("langchain4j")
                .description("Root configuration key for LangChain4j.")
        );
        classModel.addField(f -> f
                .name("MODELS_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".models")
                .description("Configuration key for model definitions.")
        );
        classModel.addField(f -> f
                .name("PROVIDERS_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".providers")
                .description("Configuration key for provider definitions.")
        );
        classModel.addField(f -> f
                .name("SERVICES_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".services")
                .description("Configuration key for service definitions.")
        );
        classModel.addField(f -> f
                .name("AGENTS_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".agents")
                .description("Configuration key for agents definitions.")
        );
        classModel.addField(f -> f
                .name("EMB_STORES_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".embedding-stores")
                .description("Configuration key for embedding store definitions.")
        );
        classModel.addField(f -> f
                .name("CONTENT_RETRIEVERS_KEY")
                .type(STRING)
                .isStatic(true)
                .isFinal(true)
                .accessModifier(PACKAGE_PRIVATE)
                .addContent("LC4J_KEY + ")
                .addContentLiteral(".content-retrievers")
                .description("Configuration key for content retrievers definitions.")
        );

        classModel.addMethod(ConstantsBuilder::addModelNamesMethod);
        classModel.addMethod(ConstantsBuilder::addCreateMethod);
        classModel.addMethod(b -> b
                .name("create")
                .isStatic(true)
                .accessModifier(PACKAGE_PRIVATE)
                .returnType(Returns.builder().type(CONFIG)
                                    .description("merged configuration"))
                .addParameter(Parameter.builder().type(CONFIG).name("root")
                                      .description("the root configuration"))
                .addParameter(Parameter.builder()
                                      .type(CLASS_WILDCARD)
                                      .name("categoryType")
                                      .description("the model class to resolve the {@link CategoryType}"))
                .addParameter(Parameter.builder().type(STRING)
                                      .name("name")
                                      .description("the name of the model"))

                .addContentLine("return create(root, resolve(categoryType), name);")
                .description("Creates a merged configuration for a model identified by {@code name}, ")
                .addDescriptionLine("combining model-specific settings with its provider's defaults."));

        classModel.addMethod(b -> b
                        .name("modelNames")
                        .isStatic(true)
                        .accessModifier(PACKAGE_PRIVATE)
                        .returnType(Returns.builder().type(TypeName.builder(LIST).addTypeArgument(STRING).build())
                                            .description("list of model names"))
                        .addParameter(Parameter.builder().type(CONFIG).name("config").description("the root configuration"))
                        .addParameter(Parameter.builder()
                                              .type(CLASS_WILDCARD)
                                              .name("categoryType")
                                              .description("model class (e.g., "
                                                                   + "{@code dev.langchain4j.model.chat.ChatLanguageModel})"))
                        .addParameter(Parameter.builder().type(STRING)
                                              .name("providerKey")
                                              .description("the provider name to filter by"))
                        .addContentLine("return modelNames(config, resolve(categoryType), providerKey);"))
                .description("Returns the names of models of the given model class");
        classModel.addMethod(ConstantsBuilder::addResolveMethod);
        classModel.addInnerClass(ConstantsBuilder::addConfigCategoryEnum);
        context.addGeneratedType(classTypeName, classModel, triggerType.typeName());
        return classTypeName;
    }

    private static void addModelNamesMethod(Method.Builder ib) {
        ib.name("modelNames")
                .isStatic(true)
                .accessModifier(PACKAGE_PRIVATE)
                .returnType(Returns.builder().type(TypeName.builder(LIST).addTypeArgument(STRING).build())
                                    .description("list of model names"))
                .addParameter(Parameter.builder().type(CONFIG).name("config").description("the root configuration"))
                .addParameter(Parameter.builder().type(INNER_CONFIG_CATEGORY_ENUM)
                                      .name("configCategory")
                                      .description("the kind of model (e.g., {@link ConfigCategory#MODEL}")
                                      .addDescriptionLine(" or {@link ConfigCategory#EMBEDDING_STORE})"))
                .addParameter(Parameter.builder().type(STRING)
                                      .name("providerKey")
                                      .description("the provider name to filter by"))
                .addContentLine("return config.get(configCategory.key)")
                .increaseContentPadding()
                .addContentLine(".asNodeList()")
                .addContentLine(".stream()")
                .addContent(".flatMap(")
                .addContent(TypeNames.COLLECTION)
                .addContentLine("::stream)")
                .addContent(".filter(c -> c.get(")
                .addContentLiteral("provider")
                .addContentLine(").asString().filter(providerKey::equals).isPresent())")
                .addContentLine(".map(Config::key)")
                .addContentLine(".map(Config.Key::name)")
                .addContentLine(".toList();")
                .description("Returns the names of models of the given {@code configCategory}")
                .addDescriptionLine(" that are configured to use")
                .addDescriptionLine(" the specified {@code providerKey}.");
    }

    private static void addCreateMethod(Method.Builder ib) {
        ib.name("create")
                .isStatic(true)
                .accessModifier(PACKAGE_PRIVATE)
                .returnType(Returns.builder().type(CONFIG)
                                    .description("merged configuration"))
                .addParameter(Parameter.builder().type(CONFIG).name("root")
                                      .description("the name of the model to create configuration for"))
                .addParameter(Parameter.builder().type(INNER_CONFIG_CATEGORY_ENUM)
                                      .name("configCategory")
                                      .description("the kind of the configuration (model or embedding store)"))
                .addParameter(Parameter.builder().type(STRING)
                                      .name("modelName")
                                      .description("the provider name to filter by"))

                .addContentLine("Config modelConfig = root.get(configCategory.key).get(modelName);")
                .addContentLine("Config providerConfig = modelConfig.get(\"provider\").asString()")
                .increaseContentPadding()
                .addContentLine(".map(providerName -> root.get(PROVIDERS_KEY).get(providerName))")
                .addContentLine(".orElse(Config.empty());")
                .addContentLine()
                .addContent("return ")
                .addContent(MERGED_CONFIG)
                .addContentLine(".create(modelConfig, providerConfig);")
                .description("Merges model and provider configurations, giving precedence to model-specific ")
                .addDescriptionLine("settings over those defined in the provider.");
    }

    private static void addResolveMethod(Method.Builder ib) {
        ib.name("resolve")
                .isStatic(true)
                .accessModifier(PACKAGE_PRIVATE)
                .returnType(Returns.builder().type(INNER_CONFIG_CATEGORY_ENUM)
                                    .description("corresponding {@link ConfigCategory}"))
                .addParameter(Parameter.builder()
                                      .type(CLASS_WILDCARD)
                                      .name("clazz")
                                      .description("class to resolve"))
                .addContent("if (")
                .addContent(LC_EMBEDDING_STORE)
                .addContentLine(".class.isAssignableFrom(clazz)) {")
                .increaseContentPadding()
                .addContentLine("return ConfigCategory.EMBEDDING_STORE;")
                .decreaseContentPadding()
                .addContentLine("}")
                .addContentLine("return ConfigCategory.MODEL;")
                .description("Resolves the {@link ConfigCategory} based on the provided class.");
    }

    private static void addConfigCategoryEnum(InnerClass.Builder ib) {
        ib.name("ConfigCategory")
                .accessModifier(PACKAGE_PRIVATE)
                .classType(ElementKind.ENUM)
                .addConstructor(cb -> cb
                        .accessModifier(PUBLIC)
                        .addParameter(STRING, "key")
                        .addContent("this.key = key;")
                )
                .addField(cf -> cf
                        .type(STRING)
                        .accessModifier(PRIVATE)
                        .isFinal(true)
                        .name("key")
                )
                .addEnumConstant(eb -> eb
                        .name("MODEL")
                        .addContent("MODELS_KEY")
                        .description("Configuration category for model definitions."))
                .addEnumConstant(eb -> eb
                        .name("EMBEDDING_STORE")
                        .addContent("EMB_STORES_KEY")
                        .description("Configuration category for embedding store definitions."))
                .addEnumConstant(eb -> eb
                        .name("CONTENT_RETRIEVER")
                        .addContent("CONTENT_RETRIEVERS_KEY")
                        .description("Configuration category for content retriever definitions."));
    }
}
