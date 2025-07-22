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

package io.helidon.declarative.codegen;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Support to generate consistent configuration for declarative types.
 */
public final class TypeConfigOverrides {
    private TypeConfigOverrides() {
    }

    /**
     * Generate a line that gets type configuration for the provided type from root configuration.
     * <p>
     * The generated code would look like this (for {@code config} for root config, and {@code typeConfig} for variable name,
     * and {@code com.example.MyType} for type name:
     * <pre>
     * var typeConfig = config.get("type-config.com.example.MyType");
     * </pre>
     *
     * @param contentBuilder     builder of the current method/constructor
     * @param typeName           type name of the type we want to retrieve configuration for
     * @param configVariableName name of the variable holding root config node
     * @param variableName       name of the variable to hold the type config node
     */
    public static void generateTypeConfig(ContentBuilder<?> contentBuilder,
                                          TypeName typeName,
                                          String configVariableName,
                                          String variableName) {
        contentBuilder.addContent("var ")
                .addContent(variableName)
                .addContent(" = ")
                .addContent(configVariableName)
                .addContent(".get(\"")
                .addContent("type-config." + typeName.fqName())
                .addContentLine("\");");
    }

    /**
     * Generate lines that get method configuration for the provided type and method from root configuration.
     * <p>
     * The generated code would look like this (for {@code config} for root config, and {@code methodConfig} for variable name,
     * and {@code com.example.MyType} for type name, and method retriable(java.lang.String):
     * <pre>
     * var methodConfig = config.get("type-config.com.example.MyType.retriable(java.lang.String)");
     * if (!methodConfig.exists()) {
     *     methodConfig = config.get("type-config.com.example.MyType.retriable");
     * }
     * </pre>
     *
     * @param contentBuilder     builder of the current method/constructor
     * @param typeName           type name of the type we want to retrieve configuration for
     * @param methodInfo         method that is to be configured
     * @param configVariableName name of the variable holding root config node
     * @param variableName       name of the variable to hold the method config node
     */
    public static void generateMethodConfig(ContentBuilder<?> contentBuilder,
                                            TypeName typeName,
                                            TypedElementInfo methodInfo,
                                            String configVariableName,
                                            String variableName) {
        contentBuilder.addContent("var ")
                .addContent(variableName)
                .addContent(" = ")
                .addContent(configVariableName)
                .addContent(".get(\"")
                .addContent("type-config." + typeName.fqName() + ".methods." + methodInfo.signature().text())
                .addContentLine("\");")
                .addContent("if (!")
                .addContent(variableName)
                .addContentLine(".exists()) {")
                .addContent(variableName)
                .addContent(" = ")
                .addContent(configVariableName)
                .addContent(".get(")
                .addContent("\"")
                .addContent("type-config." + typeName.fqName() + ".methods." + methodInfo.elementName())
                .addContentLine("\");")
                .addContentLine("}");
    }
}
