/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.codegen;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.InnerClass;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.LINKED_HASH_MAP;
import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerMap extends TypeHandlerBase {
    private static final TypeName SAME_GENERIC_TYPE = TypeName.createFromGenericDeclaration("TYPE");

    private final boolean sameGeneric;

    TypeHandlerMap(PrototypeInfo prototypeInfo, OptionInfo option) {
        super(prototypeInfo, option, option.declaredType().typeArguments().get(1));

        this.sameGeneric = option.sameGeneric();
    }

    @Override
    public Field.Builder field(boolean isBuilder) {
        Field.Builder builder = super.field(isBuilder);
        if (isBuilder && option().defaultValue().isEmpty()) {
            builder.addContent("new ")
                    .addContent(LINKED_HASH_MAP)
                    .addContent("<>()");
        }
        return builder;
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        String optionName = option().name();
        List<TypeName> typeArguments = option().declaredType().typeArguments();
        TypeName keyTypeName = typeArguments.get(0);
        TypeName valueTypeName = typeArguments.get(1);
        if (TypeNames.STRING.equals(keyTypeName) && TypeNames.STRING.equals(valueTypeName)) {
            // the special case of Map<String, String>
            method.addContentLine(configGet(optionConfigured) + ".detach().asMap().ifPresent(this::" + optionName + ");");
        } else if (optionConfigured.traverse()) {
            method.addContent(configGet(optionConfigured) + ".detach().traverse().filter(")
                    .addContent(Types.COMMON_CONFIG)
                    .addContent("::hasValue).forEach(node -> "
                                        + optionName + ".put(node.get(\"name\").asString().orElse(node.key().toString()), node");
            generateFromConfig(method);
            method.addContentLine(".get()));");
        } else {
            method.addContent(configGet(optionConfigured)
                                      + ".asNodeList().ifPresent(nodes -> nodes.forEach"
                                      + "(node -> "
                                      + optionName + ".put(node.get(\"name\").asString().orElse(node.name()), node");
            generateFromConfig(method);
            method.addContentLine(".get())));");
        }
    }

    @Override
    public void setters(InnerClass.Builder classBuilder, TypeName returnType) {

        declaredSetter(classBuilder, returnType);
        declaredSetterAdd(classBuilder, returnType);

        TypeName keyType = option().declaredType().typeArguments().get(0);
        TypeName valueType = option().declaredType().typeArguments().get(1);

        if (option().singular().isEmpty()) {
            return;
        }

        var singular = option().singular().get();
        String singularName = singular.name();
        String methodName = singular.setter().elementName();

        if (isCollection(type())) {
            // value is a collection as well we need to generate `add` methods for adding a single value, and adding
            // collection values
            // builder.addValue(String key, String value)
            // builder.addValues(String key, Set<String> values)
            setterAddValueToCollection(classBuilder,
                                       singularName,
                                       keyType,
                                       valueType,
                                       returnType);

            setterAddValuesToCollection(classBuilder,
                                        "add" + capitalize(option().name()),
                                        keyType,
                                        returnType);
        }
        // Builder putValue(String key, String value)

        var setter = option().setter();
        Javadoc setterJavadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc javadoc = Javadoc.builder(setterJavadoc)
                .add("This method adds a new value to the map, or replaces it if the key already exists.")
                .build();

        Method.Builder method = Method.builder()
                .returnType(returnType)
                .name(methodName)
                .javadoc(javadoc)
                .accessModifier(setter.accessModifier());
        if (sameGeneric) {
            sameGenericArgs(method, keyType, singularName, type());
        } else {
            method.addParameter(param -> param.name("key")
                            .type(keyType)
                            .description("key to add or replace"))
                    .addParameter(param -> param.name(singularName)
                            .type(type())
                            .description("new value for the key"));
        }
        method.addContent(Objects.class)
                .addContentLine(".requireNonNull(key);")
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + singularName + ");")
                .addContent("this." + option().name() + ".put(key, ");
        secondArgToPut(method, type(), singularName);
        method.addContentLine(");")
                .addContentLine("return self();");

        classBuilder.addMethod(method);
    }

    @Override
    TypeName setterArgumentTypeName() {
        TypeName firstType = option().declaredType().typeArguments().get(0);
        if (!(TypeNames.STRING.equals(firstType) || firstType.unboxed().primitive() || firstType.array())) {
            firstType = toWildcard(firstType);
        }
        TypeName secondType = option().declaredType().typeArguments().get(1);
        if (!(TypeNames.STRING.equals(secondType) || secondType.unboxed().primitive() || secondType.array())) {
            secondType = toWildcard(secondType);
        }

        return TypeName.builder(MAP)
                .addTypeArgument(firstType)
                .addTypeArgument(secondType)
                .build();
    }

    @Override
    void declaredSetter(InnerClass.Builder classBuilder, TypeName returnType) {
        var setter = option().setter();

        // declared type (such as Map<String, String>) - replace content
        classBuilder.addMethod(builder -> builder.name(setter.elementName())
                .returnType(returnType)
                .javadoc(Javadoc.parse(setter.description().orElse("")))
                .update(it -> {
                    for (TypedElementInfo parameter : setter.parameterArguments()) {
                        it.addParameter(param -> param.name(parameter.elementName())
                                .type(parameter.typeName()));
                    }
                })
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");")
                .addContentLine("this." + option().name() + ".clear();")
                .addContentLine("this." + option().name() + ".putAll(" + option().name() + ");")
                .addContentLine("return self();"));
    }

    private void sameGenericArgs(Method.Builder method,
                                 TypeName keyType,
                                 String value,
                                 TypeName valueType) {

        TypeName genericTypeBase;
        TypeName resolvedKeyType;
        TypeName resolvedValueType;

        if (keyType.typeArguments().isEmpty()) {
            /*
            Map<Object, List<Object>>
            <TYPE extends Object> put(TYPE, List<TYPE>)
             */
            // this is good
            genericTypeBase = keyType;
            resolvedKeyType = SAME_GENERIC_TYPE;
        } else if (keyType.typeArguments().size() == 1) {
            /*
            Map<Class<Provider>, Provider>
            <TYPE extends Provider> put(Class<TYPE>, List<TYPE>)
             */
            // this is also good
            TypeName typeArg = keyType.typeArguments().getFirst();
            if (typeArg.wildcard()) {
                // ?, or ? extends Something
                if (typeArg.generic()) {
                    genericTypeBase = OBJECT;
                } else {
                    genericTypeBase = TypeName.builder(typeArg)
                            .wildcard(false)
                            .build();
                }
            } else {
                genericTypeBase = typeArg;
            }
            resolvedKeyType = TypeName.builder(keyType)
                    .typeArguments(List.of(SAME_GENERIC_TYPE))
                    .build();
        } else {
            throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                    .fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the key generic type cannot be determined."
                                                       + " Either the key must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        method.addGenericArgument(TypeArgument.builder()
                                          .token("TYPE")
                                          .bound(genericTypeBase)
                                          .description("Type to correctly map key and value")
                                          .build());

        // now resolve value
        if (valueType.typeArguments().isEmpty()) {
            if (!genericTypeBase.equals(valueType)) {
                throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                        .fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet the type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = SAME_GENERIC_TYPE;
        } else if (valueType.typeArguments().size() == 1) {
            if (!genericTypeBase.equals(valueType.typeArguments().getFirst())) {
                throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                        .fqName() + " is "
                                                           + "annotated"
                                                           + " with @SameGeneric, yet type of value is not the"
                                                           + " same as type found on key: " + genericTypeBase.fqName());
            }
            resolvedValueType = TypeName.builder(valueType)
                    .typeArguments(List.of(SAME_GENERIC_TYPE))
                    .build();
        } else {
            throw new IllegalArgumentException("Property " + option().name() + " with type " + option().declaredType()
                    .fqName() + " is annotated"
                                                       + " with @SameGeneric, yet the value generic type cannot be determined."
                                                       + " Either the value must be a simple type, or a type with one type"
                                                       + " argument.");
        }

        method.addParameter(param -> param.name("key")
                        .type(resolvedKeyType)
                        .description("key to add or replace"))
                .addParameter(param -> param.name(value)
                        .type(resolvedValueType)
                        .description("new value for the key"));
    }

    private void setterAddValueToCollection(InnerClass.Builder classBuilder,
                                            String singularName,
                                            TypeName keyType,
                                            TypeName valueType,
                                            TypeName returnType) {
        String methodName = "add" + capitalize(singularName);
        var setter = option().setter();
        Javadoc blueprintJavadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc myJavadoc = Javadoc.builder(blueprintJavadoc)
                .parameters(Map.of())
                .addParameter("key", "key to add")
                .addParameter(singularName, "additional value for the key")
                .add("This method adds a new value to the map value, or creates a new value.")
                .build();

        classBuilder.addMethod(builder -> builder.name(methodName)
                .accessModifier(setter.accessModifier())
                .addParameter(param -> param.name("key")
                        .type(keyType))
                .addParameter(param -> param.name(singularName)
                        .type(valueType))
                .description(blueprintJavadoc.toString())
                .returnType(returnType)
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(key);")
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + singularName + ");")
                .addContentLine("this." + option().name() + ".compute(key, (k, v) -> {")
                .addContent("v = v == null ? new ")
                .addContent(LINKED_HASH_MAP)
                .addContent("<>() : new ")
                .addContent(LINKED_HASH_MAP)
                .addContentLine("<>(v);")
                .addContentLine("v.add(" + singularName + ");")
                .addContentLine("return v;")
                .decreaseContentPadding()
                .addContentLine("});")
                .addContentLine("return self();"));
    }

    private void setterAddValuesToCollection(InnerClass.Builder classBuilder,
                                             String methodName,
                                             TypeName keyType,
                                             TypeName returnType) {
        String name = option().name();
        var setter = option().setter();
        Javadoc javadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc myJavadoc = Javadoc.builder(javadoc)
                .add("This method adds a new value to the map value, or creates a new value.")
                .build();

        classBuilder.addMethod(builder -> builder.name(methodName)
                .accessModifier(setter.accessModifier())
                .addParameter(param -> param.name("key")
                        .type(keyType))
                .addParameter(param -> param.name(name)
                        .type(type()))
                .javadoc(myJavadoc)
                .returnType(returnType)
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(key);")
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + name + ");")
                .addContentLine("this." + name + ".compute(key, (k, v) -> {")
                .addContent("v = v == null ? new ")
                .addContent(LINKED_HASH_MAP)
                .addContent("<>() : new ")
                .addContent(LINKED_HASH_MAP)
                .addContentLine("<>(v);")
                .addContentLine("v.addAll(" + name + ");")
                .addContentLine("return v;")
                .decreaseContentPadding()
                .addContentLine("});")
                .addContentLine("return self();"));
    }

    private void declaredSetterAdd(InnerClass.Builder classBuilder,
                                   TypeName returnType) {
        var setter = option().setter();
        Javadoc setterJavadoc = Javadoc.parse(setter.description().orElse(""));
        Javadoc myJavadoc = Javadoc.builder(setterJavadoc)
                .add("This method keeps existing values, then puts all new values into the map.")
                .build();

        // declared type - add content
        classBuilder.addMethod(builder -> builder.name("add" + capitalize(option().name()))
                .returnType(returnType)
                .javadoc(myJavadoc)
                .addParameter(param -> param.name(option().name())
                        .type(setterArgumentTypeName()))
                .accessModifier(setter.accessModifier())
                .addContent(Objects.class)
                .addContentLine(".requireNonNull(" + option().name() + ");")
                .addContentLine("this." + option().name() + ".putAll(" + option().name() + ");")
                .addContentLine("return self();"));
    }

    private void secondArgToPut(Method.Builder method, TypeName typeName, String singularName) {
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(LIST)) {
            method.addContent(List.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else if (genericTypeName.equals(SET)) {
            method.addContent(Set.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else if (genericTypeName.equals(MAP)) {
            method.addContent(Map.class)
                    .addContent(".copyOf(" + singularName + ")");
        } else {
            method.addContent(singularName);
        }
    }

    private boolean isCollection(TypeName typeName) {
        if (typeName.typeArguments().size() != 1) {
            return false;
        }
        TypeName genericTypeName = typeName.genericTypeName();
        if (genericTypeName.equals(LIST)) {
            return true;
        }
        return genericTypeName.equals(SET);
    }

}
