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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.ElementKind.ENUM;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.COMMON_CONFIG;
import static io.helidon.config.metadata.codegen.ConfigMetadataTypes.CONFIG;

abstract class TypeHandlerBase {
    static final String UNCONFIGURED_OPTION = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    private final CodegenContext ctx;

    TypeHandlerBase(CodegenContext ctx) {
        this.ctx = ctx;
    }

    static Predicate<TypedElementInfo> isMine(TypeName type) {
        TypeName withoutGenerics = type.genericTypeName();
        return info -> info.enclosingType().map(TypeName::genericTypeName).map(withoutGenerics::equals).orElse(true);
    }

    // exactly one parameter - either common config, or Helidon config
    static boolean hasConfigParam(TypedElementInfo info) {
        List<TypedElementInfo> arguments = info.parameterArguments();
        if (arguments.size() != 1) {
            return false;
        }
        TypeName argumentType = arguments.get(0).typeName();
        return CONFIG.equals(argumentType) || COMMON_CONFIG.equals(argumentType);
    }

    /*
       Method name is camel case (such as maxInitialLineLength)
       result is dash separated and lower cased (such as max-initial-line-length).
       Note that this same method was created in ConfigUtils in common-config, but since this
       module should not have any dependencies in it a copy was left here as well.
        */
    static String toConfigKey(String methodName) {
        StringBuilder result = new StringBuilder();

        char[] chars = methodName.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.isEmpty()) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }

    static String javadoc(String docComment) {
        return Javadoc.parse(docComment);
    }

    String key(TypedElementInfo elementInfo, ConfiguredOptionData configuredOption) {
        String name = configuredOption.name();
        if (name == null || name.isBlank()) {
            return toConfigKey(elementInfo.elementName());
        }
        return name;
    }

    String description(TypedElementInfo elementInfo, ConfiguredOptionData configuredOption) {
        String desc = configuredOption.description();
        if (desc == null) {
            return javadoc(elementInfo.description().orElse(null));
        }
        return desc;
    }

    String defaultValue(String defaultValue) {
        return UNCONFIGURED_OPTION.equals(defaultValue) ? null : defaultValue;
    }

    List<ConfiguredOptionData.AllowedValue> allowedValues(ConfiguredOptionData configuredOption, TypeName type) {
        if (type.equals(configuredOption.type()) || !configuredOption.allowedValues().isEmpty()) {
            // this was already processed due to an explicit type defined in the annotation
            // or allowed values explicitly configured in annotation
            return configuredOption.allowedValues();
        }
        return allowedValues(type);
    }

    CodegenContext ctx() {
        return ctx;
    }

    List<TypeName> params(TypedElementInfo info) {
        return info.parameterArguments()
                .stream()
                .map(TypedElementInfo::typeName)
                .toList();
    }

    void addInterfaces(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        for (TypeInfo interfaceInfo : typeInfo.interfaceTypeInfo()) {
            if (interfaceInfo.hasAnnotation(requiredAnnotation)) {
                type.addInherited(interfaceInfo.typeName());
            } else {
                addSuperClasses(type, interfaceInfo, requiredAnnotation);
            }
        }
    }

    void addSuperClasses(ConfiguredType type, TypeInfo typeInfo, TypeName requiredAnnotation) {
        Optional<TypeInfo> foundSuperType = typeInfo.superTypeInfo();
        if (foundSuperType.isEmpty()) {
            return;
        }
        TypeInfo superClass = foundSuperType.get();

        while (true) {
            if (superClass.hasAnnotation(requiredAnnotation)) {
                // we only care about the first one. This one should reference its superclass/interfaces
                // if they are configured as well
                type.addInherited(superClass.typeName());
                return;
            }

            foundSuperType = superClass.superTypeInfo();
            if (foundSuperType.isEmpty()) {
                return;
            }
            superClass = foundSuperType.get();
        }
    }

    List<ConfiguredOptionData.AllowedValue> allowedValuesEnum(ConfiguredOptionData data, TypeInfo enumInfo) {
        if (!data.allowedValues().isEmpty()) {
            // this was already processed due to an explicit type defined in the annotation
            // or allowed values explicitly configured in annotation
            return data.allowedValues();
        }
        return allowedValuesEnum(enumInfo);
    }

    private List<ConfiguredOptionData.AllowedValue> allowedValuesEnum(TypeInfo enumInfo) {
        List<ConfiguredOptionData.AllowedValue> values = new ArrayList<>();
        ConfiguredOptionData.enumAllowedValues(values, enumInfo);
        return values;
    }

    private List<ConfiguredOptionData.AllowedValue> allowedValues(TypeName type) {
        return ctx().typeInfo(type)
                .filter(it -> it.kind() == ENUM)
                .map(this::allowedValuesEnum)
                .orElseGet(List::of);

    }
}
