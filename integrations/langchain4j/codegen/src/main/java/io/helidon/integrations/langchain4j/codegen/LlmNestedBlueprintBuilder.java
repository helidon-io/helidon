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

import java.util.Map;

import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

class LlmNestedBlueprintBuilder extends IntrospectionBlueprintBuilder {
    private final ClassModel.Builder classModel = ClassModel.builder();
    private final String configRoot;

    private LlmNestedBlueprintBuilder(TypeInfo modelType,
                                      TypeInfo modelBuilderType,
                                      TypeInfo lc4jNestedParentTypeInfo,
                                      String configRoot,
                                      RoundContext ctx) {
        super(ctx, modelType, lc4jNestedParentTypeInfo, modelBuilderType);
        this.configRoot = configRoot + "." + ModelCodegenHelper.providerConfigKeyFromClassName(lc4jNestedParentTypeInfo);
    }

    static LlmNestedBlueprintBuilder create(RoundContext ctx,
                                            String configRoot,
                                            TypedElementInfo srcMethod,
                                            TypeInfo lc4jProviderTypeInfo) {
        var nestedCfgAnnotation = srcMethod.findAnnotation(LangchainTypes.MODEL_NESTED_CONFIG);
        var modelType = nestedCfgAnnotation
                .flatMap(a -> a.typeValue())
                .filter(typeName -> !typeName.equals(TypeNames.BOXED_VOID))
                .flatMap(ctx::typeInfo)
                .orElseThrow();
        var modelBuilderType = nestedCfgAnnotation
                .flatMap(a -> a.stringValue("builderMethod"))
                .map(v -> ModelCodegenHelper.resolveModelBuilderType(ctx, modelType, v))
                .orElseThrow();
        var nestedParent = nestedCfgAnnotation
                .flatMap(a -> a.typeValue("parent"))
                .filter(t -> !t.equals(TypeNames.BOXED_VOID))
                .flatMap(ctx::typeInfo);

        var builder = new LlmNestedBlueprintBuilder(modelType,
                                                    modelBuilderType,
                                                    nestedParent.orElse(lc4jProviderTypeInfo),
                                                    configRoot,
                                                    ctx);

        String namePrefix = nestedParent
                .map(info -> info.typeName().className().replace("NestedParentBlueprint", ""))
                .orElse(modelType.typeName().className());

        TypeName blueprintTypeName = TypeName.builder()
                .packageName(lc4jProviderTypeInfo.typeName().packageName())
                .className(namePrefix + "ConfigBlueprint")
                .build();

        builder.initClassModel(blueprintTypeName,
                               lc4jProviderTypeInfo.typeName(),
                               nestedParent.map(info -> info.typeName()));

        return builder;
    }

    @Override
    protected String configRoot() {
        return configRoot;
    }

    @Override
    protected Map<String, TypedElementInfo> resolveOverriddenProperties() {
        // property overriding is not yet supported in nested types
        return Map.of();
    }

    @Override
    protected ClassModel.Builder classModelBuilder() {
        return classModel;
    }
}
