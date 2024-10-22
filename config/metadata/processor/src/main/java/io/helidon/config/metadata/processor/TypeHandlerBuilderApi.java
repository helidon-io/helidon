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
import java.util.Optional;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.config.metadata.processor.TypeHandlerMetaApiBase.OptionType;

import static io.helidon.config.metadata.processor.UsedTypes.CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.OPTION_CONFIGURED;

/*
 * Takes care of blueprints annotated with builder API only.
 */
class TypeHandlerBuilderApi extends TypeHandlerBase implements TypeHandler {
    private final TypeInfo blueprint;
    private final TypeName blueprintType;

    TypeHandlerBuilderApi(ProcessingEnvironment aptEnv, TypeInfo blueprint) {
        super(aptEnv);

        this.blueprint = blueprint;
        this.blueprintType = blueprint.typeName();
    }

    /*
    This is always:
    - an interface
    - uses annotations from builder-api
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

        ConfiguredAnnotation configured = ConfiguredAnnotation.createBuilder(blueprint);

        ConfiguredType type = new ConfiguredType(configured,
                                                 prototype,
                                                 targetType,
                                                 true);

        addInterfaces(type, blueprint, CONFIGURED);

        BlueprintUtil.processBlueprint(blueprint,
                                       type,
                                       prototype,
                                       targetType,
                                       OPTION_CONFIGURED,
                                       it -> processBlueprintMethod(builderType,
                                                                    type,
                                                                    it));

        return new TypeHandlerResult(targetType,
                                     module,
                                     type);
    }

    @Override
    void addInterfaces(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        BlueprintUtil.addInterfaces(type, typeInfo, requiredAnnotation);
    }

    private void processBlueprintMethod(TypeName typeName, ConfiguredType configuredType, TypedElementInfo elementInfo) {
        // we always have exactly one option per method
        ConfiguredOptionData data = ConfiguredOptionData.createBuilder(elementInfo);

        String name = key(elementInfo, data);
        String description = description(elementInfo, data);
        String defaultValue = defaultValue(data.defaultValue());
        boolean experimental = data.experimental();
        OptionType type = BlueprintUtil.typeForBlueprintFromSignature(aptMessager(), aptElements(), elementInfo, data);
        boolean optional = defaultValue != null || data.optional();
        boolean deprecated = data.deprecated();

        List<ConfiguredOptionData.AllowedValue> allowedValues;
        Optional<TypeElement> anEnum = toEnum(type.elementType());
        if (anEnum.isPresent() && defaultValue != null) {
            // prefix the default value with the enum name to make it more readable
            defaultValue = type.elementType().className() + "." + defaultValue;
            allowedValues = allowedValuesEnum(data, anEnum.get());
        } else {
            allowedValues = allowedValues(data, type.elementType());
        }

        List<TypeName> paramTypes = List.of(elementInfo.typeName());

        ConfiguredType.ProducerMethod builderMethod = new ConfiguredType.ProducerMethod(false,
                                                                                        typeName,
                                                                                        elementInfo.elementName(),
                                                                                        paramTypes);

        ConfiguredType.ConfiguredProperty property = new ConfiguredType.ConfiguredProperty(builderMethod.toString(),
                                                                                           name,
                                                                                           description,
                                                                                           defaultValue,
                                                                                           type.elementType(),
                                                                                           experimental,
                                                                                           optional,
                                                                                           type.kind(),
                                                                                           data.provider(),
                                                                                           data.providerType(),
                                                                                           deprecated,
                                                                                           data.merge(),
                                                                                           allowedValues);
        configuredType.addProperty(property);
    }

}
