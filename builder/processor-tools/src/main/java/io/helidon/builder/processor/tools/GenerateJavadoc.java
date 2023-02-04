/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.tools;

import io.helidon.builder.types.TypeName;
import io.helidon.builder.types.TypedElementName;

final class GenerateJavadoc {
    private GenerateJavadoc() {
    }

    static void typeConstructorWithBuilder(StringBuilder builder) {
        builder.append("\n\t/**\n"
                               + "\t * Constructor using the builder argument.\n"
                               + "\t *\n"
                               + "\t * @param b\tthe builder\n"
                               + "\t */\n");
    }

    static void builderClass(StringBuilder builder,
                             BodyContext ctx) {
        builder.append("\n\t/**\n\t * Fluent API builder for {@code ")
                .append(ctx.genericBuilderAcceptAliasDecl())
                .append("}.\n\t *\n");
        if (!ctx.doingConcreteType()) {
            builder.append("\t * @param <").append(ctx.genericBuilderAliasDecl()).append(">\tthe type of the builder\n");
            builder.append("\t * @param <").append(ctx.genericBuilderAcceptAliasDecl())
                    .append(">\tthe type of the built instance\n");
        }
        builder.append("\t */\n");
    }

    static void builderMethod(StringBuilder builder,
                              BodyContext ctx) {
        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type.\n"
                               + "\t *\n");
        builder.append("\t * @return a builder for {@link ");
        builder.append(ctx.typeInfo().typeName());
        builder.append("}\n\t */\n");
    }

    static void toBuilderMethod(StringBuilder builder,
                                BodyContext ctx) {
        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type, initialized with the attributes from the values passed.\n"
                               + "\t *\n");
        builder.append("\t * @param val the value to copy to initialize the builder attributes\n");
        builder.append("\t * @return a builder for {@link ").append(ctx.typeInfo().typeName());
        builder.append("}\n\t */\n");
    }

    static void builderConstructor(StringBuilder builder) {
        builder.append("\t\t/**\n"
                               + "\t\t * Fluent API builder constructor.\n"
                               + "\t\t */\n");
    }

    static void identity(StringBuilder builder) {
        builder.append("\t\t/**\n"
                               + "\t\t * Instance of this builder as the correct type.\n"
                               + "\t\t *\n"
                               + "\t\t * @return this instance typed to correct type\n"
                               + "\t\t */\n");
    }

    static void accept(StringBuilder builder) {
        builder.append("\t\t/**\n"
                               + "\t\t * Accept and update from the provided value object.\n"
                               + "\t\t *\n"
                               + "\t\t * @param val the value object to copy from\n"
                               + "\t\t * @return this instance typed to correct type\n"
                               + "\t\t */\n");
    }

    static void buildMethod(StringBuilder builder) {
        builder.append("\t\t/**\n"
                               + "\t\t * Builds the instance.\n"
                               + "\t\t *\n"
                               + "\t\t * @return the built instance\n"
                               + "\t\t * @throws IllegalArgumentException if any required attributes are missing\n"
                               + "\t\t */\n");
    }

    static void updateConsumer(StringBuilder builder) {
        builder.append("\t\t/**\n"
                               + "\t\t * Update the builder in a fluent API way.\n"
                               + "\t\t *\n"
                               + "\t\t * @param consumer consumer of the builder instance\n"
                               + "\t\t * @return updated builder instance\n"
                               + "\t\t */\n");
    }

    static void builderField(StringBuilder builder,
                             TypedElementName method) {
        builder.append("\t\t/**\n" + "\t\t * Field value for {@code ")
                .append(method)
                .append("()}.\n\t\t */\n");
    }

    static void innerToString(StringBuilder builder) {
        builder.append("\t/**\n"
                               + "\t * Produces the inner portion of the toString() output (i.e., what is between the parens).\n"
                               + "\t *\n"
                               + "\t * @return portion of the toString output\n"
                               + "\t */\n");
    }

    static void setter(StringBuilder builder,
                       String beanAttributeName) {
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for '").append(beanAttributeName).append("'.\n");
        builder.append("\t\t *\n");
        builder.append("\t\t * @param val the new value\n");
        builder.append("\t\t * @return this fluent builder\n");
        builder.append("\t\t */\n");
    }

    static void singularSetter(StringBuilder builder,
                               TypeName methodTypeName,
                               String beanAttributeName) {
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for '").append(beanAttributeName).append("'.\n");
        builder.append("\t\t *\n");
        if (methodTypeName.isMap()) {
            builder.append("\t\t * @param key the key\n");
        }
        builder.append("\t\t * @param val the new value\n");
        builder.append("\t\t * @return this fluent builder\n");
        builder.append("\t\t */\n");
    }

    static void internalMetaAttributes(StringBuilder builder) {
        builder.append("\t/**\n"
                               + "\t * The map of meta attributes describing each element of this type.\n"
                               + "\t *\n"
                               + "\t * @return the map of meta attributes using the key being the attribute name\n"
                               + "\t */\n");
    }

    static void internalMetaPropsField(StringBuilder builder) {
        builder.append("\t/**\n"
                               + "\t * Meta properties, statically cached.\n"
                               + "\t */\n");
    }

    static void visitAttributes(StringBuilder builder,
                                BodyContext ctx,
                                String extraTabs) {
        builder.append(extraTabs).append("\t/**\n");
        builder.append(extraTabs).append("\t * Visits all attributes of " + ctx.typeInfo().typeName() + ", calling the {@link "
                                                 + "AttributeVisitor} for each.\n");
        builder.append(extraTabs).append("\t *\n");
        builder.append(extraTabs).append("\t * @param visitor\t\t\tthe visitor called for each attribute\n");
        builder.append(extraTabs).append("\t * @param userDefinedCtx\tany object you wish to pass to each visit call\n");
        builder.append(extraTabs).append("\t * @param <T> type of the user defined context\n");
        builder.append(extraTabs).append("\t */\n");
    }
}
