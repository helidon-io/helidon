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

package io.helidon.config.metadata.codegen;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.ElementKind.ENUM;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.BLUEPRINT;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.COMMON_CONFIG;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.CONFIGURED;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.OPTION_CONFIGURED;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.PROTOTYPE_FACTORY;

/*
 * Takes care of blueprints annotated with builder API only.
 */
class TypeHandlerBuilderApi extends TypeHandlerBase implements TypeHandler {
    private final TypeInfo blueprint;
    private final TypeName blueprintType;

    TypeHandlerBuilderApi(CodegenContext ctx, TypeInfo blueprint) {
        super(ctx);

        this.blueprint = blueprint;
        this.blueprintType = blueprint.typeName();
    }

    static TypeHandler create(CodegenContext ctx, TypeInfo typeInfo) {
        return new TypeHandlerBuilderApi(ctx, typeInfo);
    }

    /*
    This is always:
    - an interface
    - uses annotations from builder-api
    - return type is the one to use
     */
    @Override
    public TypeHandlerResult handle() {
        TypeName prototype = prototype(blueprintType);
        TypeName builderType = TypeName.builder(prototype)
                .className("Builder")
                .addEnclosingName(prototype.className())
                .build();
        TypeName targetType = targetType(blueprint, prototype);
        String module = blueprint.module().orElse("unknown");

        ConfiguredAnnotation configured = ConfiguredAnnotation.createBuilder(blueprint);

        ConfiguredType type = new ConfiguredType(configured,
                                                 prototype,
                                                 targetType,
                                                 true);

        addInterfaces(type, blueprint, CONFIGURED);

        type.addProducer(new ConfiguredType.ProducerMethod(true, prototype, "create", List.of(COMMON_CONFIG)));
        type.addProducer(new ConfiguredType.ProducerMethod(true, prototype, "builder", List.of()));

        if (!targetType.equals(prototype)) {
            // target type may not have the method (if it is for example PrivateKey)
            type.addProducer(new ConfiguredType.ProducerMethod(false, targetType, "create", List.of(prototype)));
        }

        // and now process all blueprint methods - must be non default, non static
        // methods on this interface

        blueprint.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(TypeHandlerBase.isMine(blueprint.typeName()))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(Predicate.not(ElementInfoPredicates::isDefault))
                .filter(ElementInfoPredicates.hasAnnotation(OPTION_CONFIGURED))
                .forEach(it -> processBlueprintMethod(builderType,
                                                      type,
                                                      it));

        return new TypeHandlerResult(targetType,
                                     module,
                                     type);
    }

    @Override
    void addInterfaces(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            if (interfaceInfo.hasAnnotation(requiredAnnotation)) {
                TypeName ifaceTypeName = interfaceInfo.typeName();

                if (interfaceInfo.hasAnnotation(BLUEPRINT)) {
                    String className = ifaceTypeName.className();
                    if (className.endsWith("Blueprint")) {
                        className = className.substring(0, className.length() - "Blueprint".length());
                    }
                    ifaceTypeName = TypeName.builder(ifaceTypeName)
                            .className(className)
                            .build();
                }

                type.addInherited(ifaceTypeName);
            } else {
                addSuperClasses(type, interfaceInfo, requiredAnnotation);
            }
        }
    }

    private static TypeName prototype(TypeName blueprintType) {
        String className = blueprintType.className();
        if (className.endsWith("Blueprint")) {
            className = className.substring(0, className.length() - "Blueprint".length());
        }
        return TypeName.builder(blueprintType)
                .className(className)
                .build()
                .genericTypeName();
    }

    // if the type implements `Factory<X>`, we want to return X, otherwise "pure" config object
    private static TypeName targetType(TypeInfo blueprint, TypeName prototype) {
        return blueprint.interfaceTypeInfo()
                .stream()
                .map(TypeInfo::typeName)
                .filter(it -> PROTOTYPE_FACTORY.equals(it.genericTypeName()))
                .filter(it -> it.typeArguments().size() == 1)
                .map(it -> it.typeArguments().get(0))
                .findAny()
                .orElse(prototype);
    }

    private OptionType typeForBlueprintFromSignature(TypedElementInfo element,
                                                     ConfiguredOptionData annotation) {
        // guess from method

        if (!ElementInfoPredicates.hasNoArgs(element)) {
            throw new CodegenException("Method " + element + " is annotated with @Configured, "
                                               + "yet it has a parameter. Interface methods must not have parameters.",
                                       element.originatingElementValue());
        }

        TypeName returnType = element.typeName();
        if (ElementInfoPredicates.isVoid(element)) {
            throw new CodegenException("Method " + element + " is annotated with @Configured, "
                                               + "yet it is void. Interface methods must return the property type.",
                                       element.originatingElementValue());
        }

        if (returnType.isOptional()) {
            // may be an optional of list etc.
            if (!(returnType.isMap() || returnType.isSet() || returnType.isList())) {
                return new OptionType(returnType.typeArguments().get(0), "VALUE");
            }
            returnType = returnType.typeArguments().get(0);
        }

        if (returnType.isList() || returnType.isSet()) {
            return new OptionType(returnType.typeArguments().get(0), "LIST");
        }

        if (returnType.isMap()) {
            return new OptionType(returnType.typeArguments().get(1), "MAP");
        }

        return new OptionType(returnType.boxed(), annotation.kind());
    }

    private void processBlueprintMethod(TypeName typeName, ConfiguredType configuredType, TypedElementInfo elementInfo) {
        // we always have exactly one option per method
        ConfiguredOptionData data = ConfiguredOptionData.createBuilder(elementInfo);

        String name = key(elementInfo, data);
        String description = description(elementInfo, data);
        String defaultValue = defaultValue(data.defaultValue());
        boolean experimental = data.experimental();
        OptionType type = typeForBlueprintFromSignature(elementInfo, data);
        boolean optional = defaultValue != null || data.optional();
        boolean deprecated = data.deprecated();

        Optional<TypeInfo> enumType = ctx().typeInfo(type.elementType())
                .filter(it -> it.kind() == ENUM);

        List<ConfiguredOptionData.AllowedValue> allowedValues;

        if (enumType.isPresent() && defaultValue != null) {
            // prefix the default value with the enum name to make it more readable
            defaultValue = type.elementType().className() + "." + defaultValue;
            allowedValues = allowedValuesEnum(data, enumType.get());
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
