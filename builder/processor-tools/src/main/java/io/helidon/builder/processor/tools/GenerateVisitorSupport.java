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

/**
 * See {@link io.helidon.builder.RequiredAttributeVisitor} for the prototypical output for this generated class.
 */
final class GenerateVisitorSupport {
    private GenerateVisitorSupport() {
    }

    static void appendExtraInnerClasses(
            StringBuilder builder,
            BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        if (!ctx.hasParent()
                && ctx.includeMetaAttributes()
                && !ctx.requireLibraryDependencies()) {
            builder.append("\n\n\t/**\n"
                                   + "\t * A functional interface that can be used to visit all attributes of this type.\n"
                                   + "\t *\n"
                                   + "\t * @param <T> type of user defined context"
                                   + "\t */\n");
            builder.append("\t@FunctionalInterface\n"
                                   + "\tpublic static interface AttributeVisitor<T> {\n"
                                   + "\t\t/**\n"
                                   + "\t\t * Visits the attribute named 'attrName'.\n"
                                   + "\t\t *\n"
                                   + "\t\t * @param attrName\t\tthe attribute name\n"
                                   + "\t\t * @param valueSupplier\tthe attribute value supplier\n"
                                   + "\t\t * @param meta\t\t\tthe meta information for the attribute\n"
                                   + "\t\t * @param userDefinedCtx a user defined context that can be used for holding an "
                                   + "object of your choosing\n"
                                   + "\t\t * @param type\t\t\tthe type of the attribute\n"
                                   + "\t\t * @param typeArgument\tthe type arguments (if type is a parameterized / generic "
                                   + "type)\n"
                                   + "\t\t */\n"
                                   + "\t\tvoid visit(String attrName, Supplier<Object> valueSupplier, "
                                   + "Map<String, Object> meta, T userDefinedCtx, Class<?> "
                                   + "type, Class<?>... typeArgument);\n"
                                   + "\t}");

            builder.append("\n\n\t/**\n"
                                   + "\t * An implementation of {@link AttributeVisitor} that will validate each attribute to "
                                   + "enforce not-null. The source\n"
                                   + "\t * must be annotated with {@code ConfiguredOption(required=true)} for this to be "
                                   + "enforced.\n"
                                   + "\t */\n");
            builder.append("\tprotected static class RequiredAttributeVisitor implements AttributeVisitor<Object> {\n"
                                   + "\t\tprivate final List<String> errors = new java.util.ArrayList();\n"
                                   + "\t\tprivate final boolean allowNullsByDefault;\n"
                                   + "\n"
                                   + "\t\t/**\n"
                                   + "\t\t * Default Constructor.\n"
                                   + "\t\t */\n"
                                   + "\t\tprotected RequiredAttributeVisitor() {\n"
                                   + "\t\t\tthis(" + ctx.allowNulls() + ");\n"
                                   + "\t\t}\n\n");
            builder.append("\t\t/**\n"
                                   + "\t\t * Constructor.\n"
                                   + "\t\t *\n"
                                   + "\t\t * @param allowNullsByDefault true if nulls should be allowed\n"
                                   + "\t\t */\n"
                                   + "\t\tpublic RequiredAttributeVisitor(boolean allowNullsByDefault) {\n"
                                   + "\t\t\tthis.allowNullsByDefault = allowNullsByDefault;\n"
                                   + "\t\t}\n\n");
            builder.append("\t\t@Override\n"
                                   + "\t\tpublic void visit(String attrName,\n"
                                   + "\t\t\t\t\t\t  Supplier<Object> valueSupplier,\n"
                                   + "\t\t\t\t\t\t  Map<String, Object> meta,\n"
                                   + "\t\t\t\t\t\t  Object userDefinedCtx,\n"
                                   + "\t\t\t\t\t\t  Class<?> type,\n"
                                   + "\t\t\t\t\t\t  Class<?>... typeArgument) {\n"
                                   + "\t\t\tString requiredStr = (String) meta.get(\"required\");\n"
                                   + "\t\t\tboolean requiredPresent = Objects.nonNull(requiredStr);\n"
                                   + "\t\t\tboolean required = Boolean.parseBoolean(requiredStr);\n"
                                   + "\t\t\tif (!required && requiredPresent) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n"
                                   + "\n"
                                   + "\t\t\tif (allowNullsByDefault && !requiredPresent) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n"
                                   + "\t\t\t\n"
                                   + "\t\t\tObject val = valueSupplier.get();\n"
                                   + "\t\t\tif (Objects.nonNull(val)) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n"
                                   + "\t\t\t\n"
                                   + "\t\t\terrors.add(\"'\" + attrName + \"' is a required attribute and should not be null\")"
                                   + ";\n"
                                   + "\t\t}\n"
                                   + "\n"
                                   + "\t\tvoid validate() {\n"
                                   + "\t\t\tif (!errors.isEmpty()) {\n"
                                   + "\t\t\t\tthrow new java.lang.IllegalStateException(String.join(\", \", errors));\n"
                                   + "\t\t\t}\n"
                                   + "\t\t}\n"
                                   + "\t}\n");
        }
    }

}
