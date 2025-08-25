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

import static io.helidon.declarative.codegen.DeclarativeTypes.CONFIG_BUILDER_SUPPORT;

/**
 * Support to generate consistent configuration for declarative types.
 */
public final class DelcarativeConfigSupport {
    private DelcarativeConfigSupport() {
    }

    /**
     * Create an assignment for an expression that can use configuration references.
     *
     * @param contentBuilder     builder of content to add lines to
     * @param configVariableName name of the config variable (i.e. {@code config})
     * @param variableName       name of the variable to assign to (i.e. {@code uri})
     * @param expression         the expression to resolve, probably from annotation option
     */
    public static void assignResolveExpression(ContentBuilder<?> contentBuilder,
                                               String configVariableName,
                                               String variableName,
                                               String expression) {
        contentBuilder.addContent("var ")
                .addContent(variableName)
                .addContent(" = ")
                .addContent(CONFIG_BUILDER_SUPPORT)
                .addContent(".resolveExpression(")
                .addContent(configVariableName)
                .addContent(", ")
                .addContentLiteral(expression)
                .addContentLine(");");
    }

    /**
     * Create an in-lined expression resolution that can use configuration references.
     *
     * @param contentBuilder     builder of content to add lines to
     * @param configVariableName name of the config variable (i.e. {@code config})
     * @param expression         the expression to resolve, probably from annotation option
     */
    public static void resolveExpression(ContentBuilder<?> contentBuilder,
                                         String configVariableName,
                                         String expression) {
        contentBuilder.addContent(CONFIG_BUILDER_SUPPORT)
                .addContent(".resolveExpression(")
                .addContent(configVariableName)
                .addContent(", ")
                .addContentLiteral(expression)
                .addContent(")");
    }
}
