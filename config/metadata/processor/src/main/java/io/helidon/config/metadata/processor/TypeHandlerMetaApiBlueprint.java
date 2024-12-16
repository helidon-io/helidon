/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.processor;

import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTION;

/*
 * Takes care of blueprints annotated with config metadata annotations.
 */
class TypeHandlerMetaApiBlueprint extends TypeHandlerMetaApiBase implements TypeHandler {
    private final TypeInfo blueprint;
    private final TypeName blueprintType;

    TypeHandlerMetaApiBlueprint(ProcessingEnvironment aptEnv, TypeInfo blueprint) {
        super(aptEnv);

        this.blueprint = blueprint;
        this.blueprintType = blueprint.typeName();
    }

    /*
    This is always:
    - an interface
    - uses annotations from config-metadata
    - return type is the one to use
     */
    @Override
    public TypeHandlerResult handle() {
        TypeName prototype = BlueprintUtil.prototype(blueprintType);
        TypeName builderType = TypeName.builder(prototype)
                .className("Builder")
                .addEnclosingName(prototype.className())
                .build();
        TypeName targetType = BlueprintUtil.targetType(blueprint, prototype);
        String module = blueprint.module().orElse("unknown");

        ConfiguredAnnotation configured = ConfiguredAnnotation.createMeta(blueprint.annotation(META_CONFIGURED));

        ConfiguredType type = new ConfiguredType(configured,
                                                 prototype,
                                                 targetType,
                                                 true);

        addInterfaces(type, blueprint, META_CONFIGURED);

        BlueprintUtil.processBlueprint(blueprint,
                                       type,
                                       prototype,
                                       targetType,
                                       META_OPTION,
                                       it -> processBuilderMethod(builderType,
                                                                  type,
                                                                  it,
                                                                  this::optionType,
                                                                  this::builderMethodParams));

        return new TypeHandlerResult(targetType,
                                     module,
                                     type);
    }

    @Override
    void addInterfaces(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        BlueprintUtil.addInterfaces(type, typeInfo, requiredAnnotation);
    }

    private List<TypeName> builderMethodParams(TypedElementInfo elementInfo, OptionType type) {
        return List.of(type.elementType());
    }

    private OptionType optionType(TypedElementInfo elementInfo, ConfiguredOptionData configuredOption) {
        if (configuredOption.type() == null || configuredOption.type().equals(META_OPTION)) {
            return BlueprintUtil.typeForBlueprintFromSignature(aptMessager(), aptElements(), elementInfo, configuredOption);
        } else {
            // use the one defined on annotation
            return new OptionType(configuredOption.type(), configuredOption.kind());
        }
    }
}
