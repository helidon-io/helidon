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
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.Returns;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class ConfigureMethodBuilder {

    private final Method.Builder confMethodBuilder = Method.builder();
    private final List<TypedElementInfo> overriddenProperties = new ArrayList<>();

    ConfigureMethodBuilder(TypeInfo parentTypeInfo,
                           TypeInfo builderTypeInfo,
                           TypeName modelTypeName) {
        confMethodBuilder
                .name("configuredBuilder")
                .returnType(Returns.builder()
                                    .description("Actual Lc4j model builder configured with this blueprint.")
                                    .type(builderTypeInfo.typeName())
                                    .build())
                .isDefault(true)
                .addDescriptionLine("<b>Skipped:</b>")
                .addDescriptionLine("<ul>");

        var customBuilderMappingMethod = parentTypeInfo.elementInfo().stream()
                .filter(m -> m.kind().equals(ElementKind.METHOD))
                .filter(m -> m.parameterArguments().isEmpty())
                .filter(m -> m.typeName().equals(builderTypeInfo.typeName()))
                .filter(m -> m.elementName().equals("configuredBuilder"))
                .findFirst();

        if (customBuilderMappingMethod.isPresent()) {
            confMethodBuilder
                    .addContent("var modelBuilder = ")
                    .addContent(parentTypeInfo.typeName())
                    .addContent(".super.configuredBuilder(")
                    .addContentLine(");");
        } else {
            confMethodBuilder.addContent("var modelBuilder = ").addContent(modelTypeName).addContentLine(".builder();");
        }
    }

    void commentOverriddenProperty(TypedElementInfo modelBldMethod) {
        overriddenProperties.add(modelBldMethod);
    }

    void commentSkippedProperty(TypedElementInfo modelBldMethod, String reason) {
        confMethodBuilder.addDescriptionLine("<li>" + modelBldMethod.signature().name() + " - " + reason + "</li>");
    }

    void configureProperty(String propName,
                           TypeName propType,
                           boolean isNestedType,
                           boolean toArray,
                           boolean isOptional) {

        boolean isCollection = propType.isList() || propType.isSet() || propType.isMap() || propType.array();

        //             this.propName()  .ifPresent(modelBuilder::propName);
        // Optional.of(this.propName()) .ifPresent(modelBuilder::propName);
        // this.propName().stream()     .ifPresent(modelBuilder::propName);

        // Optional.of(this.propName().filter(c -> !c.isEmpty()).flatMap(c -> c.stream()).map(c -> c.configuredBuilder().build
        // ())).ifPresent(modelBuilder::propName);

        String line;
        if (isOptional || isCollection) {
            line = "this." + propName + "()";
        } else {
            line = "Optional.of(this." + propName + "())";
        }

        if (propType.isMap()) {
            line += ".entrySet()";
        }

        if (isCollection) {
            line += ".stream()";
        }

        if (isNestedType) {
            line += ".map(c -> c.configuredBuilder().build())";
        }

        if (propType.isList()) {
            line += ".collect(java.util.stream.Collectors.toList())";
        }

        if (propType.isSet()) {
            line += ".collect(java.util.stream.Collectors.toSet())";
        }

        if (propType.array()) {
            line += ".toArray(" + propType.genericTypeName().fqName() + "[]::new)";
        }

        if (propType.isMap()) {
            line += ".collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue))";
        }

        if (isCollection) {
            line = "Optional.of(" + line + ")";
        }

        line += ".ifPresent(p -> modelBuilder." + propName + "(p))";

        confMethodBuilder.addContentLine(line + ";");
    }

    Method build(){
        confMethodBuilder.addContentLine("return modelBuilder;");

        confMethodBuilder.addDescriptionLine("</ul>");
        if (!overriddenProperties.isEmpty()) {
            confMethodBuilder.addDescriptionLine("<p>");
            confMethodBuilder.addDescriptionLine("<b>Overridden:</b>");
            confMethodBuilder.addDescriptionLine("<ul>");
            overriddenProperties
                    .forEach(p -> {
                        var className = p.enclosingType().orElseThrow().className();
                        var signature = p.signature().text();
                        confMethodBuilder.addDescriptionLine("<li>{@link " + className + "#" + signature + "}</li>");
                    });
            confMethodBuilder.addDescriptionLine("</ul>");
        }

        return confMethodBuilder.build();
    }
}
