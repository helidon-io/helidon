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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.common.types.TypeInfo;

final class ModelCodegenHelper {

    private static final Pattern LC4J_PROVIDER_NAME_MASK =
            Pattern.compile("([A-Z][A-Za-z0-9]*)(Lc4jProvider|NestedParentBlueprint)");

    private ModelCodegenHelper() {
        // noop
    }

    static String providerConfigKeyFromClassName(TypeInfo typeInfo) {
        return camelToKebabCase(providerFromClassName(typeInfo));
    }

    static TypeInfo resolveModelBuilderType(RoundContext roundContext, TypeInfo typeInfo, String builderMethodName) {
        // Explicitly set builder has priority
        return typeInfo.elementInfo().stream()
                .filter(m -> m.elementName().equals(builderMethodName))
                .filter(m -> m.parameterArguments().isEmpty())
                .map(m -> m.typeName())
                .findFirst().flatMap(roundContext::typeInfo)
                .orElseThrow(() -> new CodegenException("Builder type not found for " + typeInfo.typeName()));
    }

    static String providerFromClassName(TypeInfo typeInfo) {
        var providerClassName = typeInfo.typeName().className();
        Matcher m = LC4J_PROVIDER_NAME_MASK.matcher(providerClassName);
        if (m.matches()) {
            return m.group(1);
        }

        throw new CodegenException("Unable to determine provider name from class name: "
                                           + providerClassName
                                           + ", provider name must be in format: "
                                           + LC4J_PROVIDER_NAME_MASK.pattern());
    }

    static String camelToKebabCase(String in) {
        var sb = new StringBuilder();
        var chars = in.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (i == 0) {
                sb.append(Character.toLowerCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
