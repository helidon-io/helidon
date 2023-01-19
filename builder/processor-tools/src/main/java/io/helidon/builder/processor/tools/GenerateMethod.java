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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

final class GenerateMethod {
    static final String SINGULAR_PREFIX = "add";

    private GenerateMethod() {
    }

    static String builderMethods(StringBuilder builder,
                               BodyContext ctx) {
        GenerateJavadoc.builderMethod(builder, ctx);
        builder.append("\tpublic static Builder");
        builder.append(" builder() {\n");
        builder.append("\t\treturn new Builder();\n");
        builder.append("\t}\n\n");

        GenerateJavadoc.toBuilderMethod(builder, ctx);
        builder.append("\tpublic static Builder");
        builder.append(" toBuilder(").append(ctx.ctorBuilderAcceptTypeName()).append(" val) {\n");
        builder.append("\t\tObjects.requireNonNull(val);\n");
        builder.append("\t\treturn builder().accept(val);\n");
        builder.append("\t}\n\n");

        return "public static Builder toBuilder({args})";
    }

    static void stringToCharSetter(StringBuilder builder,
                                   BodyContext ctx,
                                   String beanAttributeName,
                                   TypedElementName method,
                                   String methodName) {
        GenerateJavadoc.setter(builder, beanAttributeName);
        builder.append("\t\tpublic ").append(ctx.genericBuilderAliasDecl()).append(" ").append(methodName)
                .append("(String val) {\n");
        builder.append("\t\t\tObjects.requireNonNull(val);\n");
        builder.append("\t\t\treturn this.")
                .append(methodName)
                .append("(val.toCharArray());\n");
        builder.append("\t\t}\n\n");
    }

    static void internalMetaAttributes(StringBuilder builder) {
        GenerateJavadoc.internalMetaAttributes(builder);
        builder.append("\tpublic static Map<String, Map<String, Object>> __metaAttributes() {\n"
                               + "\t\treturn ").append(BodyContext.TAG_META_PROPS).append(";\n"
                               + "\t}\n\n");
    }

    static void nonOptionalSetter(StringBuilder builder,
                                  BodyContext ctx,
                                  String beanAttributeName,
                                  TypedElementName method,
                                  String methodName,
                                  TypeName genericType) {
        GenerateJavadoc.setter(builder, beanAttributeName);
        builder.append("\t\tpublic ")
                .append(ctx.genericBuilderAliasDecl())
                .append(" ")
                .append(methodName)
                .append("(")
                .append(genericType.fqName())
                .append(" val) {\n");
        builder.append("\t\t\tObjects.requireNonNull(val);\n");
        builder.append("\t\t\treturn ")
                .append(beanAttributeName)
                .append("(")
                .append(Optional.class.getName())
                .append(".of(val));\n");
        builder.append("\t\t}\n\n");

        if ("char[]".equals(genericType.fqName())) {
            stringToCharSetter(builder, ctx, beanAttributeName, method, methodName);
        }
    }

    static void singularSetter(StringBuilder builder,
                               BodyContext ctx,
                               TypedElementName method,
                               String beanAttributeName,
                               char[] methodName) {
        TypeName typeName = method.typeName();
        TypeName mapValueType = mapValueTypeNameOf(typeName);
        builder.append(
                oneSingularSetter(ctx,
                                  typeName,
                                  toGenericsDecl(method, false, mapValueType),
                                  beanAttributeName,
                                  methodName,
                                  false,
                                  mapValueType));

        // check if the values of the map are collection types, and overload that style method as well for singular usage
        if (mapValueType != null && !mapValueType.typeArguments().isEmpty()) {
            if (mapValueType.isSet() || mapValueType.isList()) {
                TypeName singularMapValueType = Objects.requireNonNull(mapValueType.typeArguments().get(0));
                builder.append(
                        oneSingularSetter(ctx,
                                          typeName,
                                          toGenericsDecl(method, true, singularMapValueType),
                                          beanAttributeName,
                                          methodName,
                                          true,
                                          mapValueType));
            }
        }
    }

    private static StringBuilder oneSingularSetter(BodyContext ctx,
                                                   TypeName typeName,
                                                   String genericDecl,
                                                   String beanAttributeName,
                                                   char[] methodName,
                                                   boolean forceUseComputeStyle,
                                                   TypeName mapValueType) {
        StringBuilder builder = new StringBuilder();
        GenerateJavadoc.singularSetter(builder, typeName, beanAttributeName);

        // builder method declaration for "addSomething()"
        builder.append("\t\tpublic ")
                .append(ctx.genericBuilderAliasDecl())
                .append(" ").append(SINGULAR_PREFIX)
                .append(methodName)
                .append("(")
                .append(genericDecl)
                .append(") {\n");
        // body of the method
        if (typeName.isMap()) {
            builder.append("\t\t\tObjects.requireNonNull(key);\n");
        }
        builder.append("\t\t\tObjects.requireNonNull(val);\n");

        builder.append("\t\t\tthis.").append(beanAttributeName);
        if (typeName.isList() || typeName.isSet()) {
            builder.append(".add(val);\n");
        } else { // isMap
            boolean useComputeStyle = forceUseComputeStyle
                    || (mapValueType != null && (mapValueType.isSet() || mapValueType.isList() || mapValueType.isMap()));
            if (useComputeStyle) {
                builder.append(".compute(key, (k, v) -> {\n");
                builder.append("\t\t\t\tif (v == null) {\n");
                builder.append("\t\t\t\t\tv = new ");
                if (mapValueType.isSet()) {
                    builder.append(ctx.setType());
                } else if (mapValueType.isList()) {
                    builder.append(ctx.listType());
                } else if (mapValueType.isMap()) {
                    builder.append(ctx.mapType());
                } else {
                    throw new IllegalStateException("unhandled singular type: " + mapValueType);
                }
                builder.append("<>();\n");
                builder.append("\t\t\t\t}\n");
                if (forceUseComputeStyle) {
                    if (mapValueType.isSet() || mapValueType.isList()) {
                        builder.append("\t\t\t\t((java.util.Collection) v).add(val);\n");
                    } else if (mapValueType.isMap()) {
                        builder.append("\t\t\t\t((java.util.Map) v).put(k, val);\n");
                    } else {
                        throw new IllegalStateException("unhandled singular type: " + mapValueType);
                    }
                } else {
                    if (mapValueType.isSet() || mapValueType.isList()) {
                        builder.append("\t\t\t\t((java.util.Collection) v).addAll(val);\n");
                    } else if (mapValueType.isMap()) {
                        builder.append("\t\t\t\t((java.util.Map) v).putAll(val);\n");
                    } else {
                        builder.append("\t\t\t\t((java.util.Map) v).put(k, val);\n");
                    }
                }
                builder.append("\t\t\t\treturn v;\n");
                builder.append("\t\t\t});\n");
            } else {
                builder.append(".put(key, val);\n");
            }
        }
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");
        return builder;
    }

    private static TypeName mapValueTypeNameOf(TypeName typeName) {
        return (typeName.isMap() && typeName.typeArguments().size() > 1) ? typeName.typeArguments().get(1) : null;
    }

    private static String toGenericsDecl(TypedElementName method,
                                         boolean useSingluarMapValues,
                                         TypeName mapValueType) {
        List<TypeName> compTypeNames = method.typeName().typeArguments();
        if (1 == compTypeNames.size()) {
            return avoidWildcard(compTypeNames.get(0)) + " val";
        } else if (2 == compTypeNames.size()) {
            if (useSingluarMapValues) {
                return avoidWildcard(compTypeNames.get(0)) + " key, " + avoidWildcard(mapValueType) + " val";
            } else {
                return avoidWildcard(compTypeNames.get(0)) + " key, " + avoidWildcard(compTypeNames.get(1)) + " val";
            }
        }
        return "Object val";
    }

    private static String avoidWildcard(TypeName typeName) {
        return typeName.wildcard() ? typeName.name() : typeName.fqName();
    }

}
