/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;
import io.helidon.codegen.classmodel.ClassBase;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.common.types.TypeNames.LIST;
import static io.helidon.common.types.TypeNames.MAP;
import static io.helidon.common.types.TypeNames.SET;

class TypeHandlerOptional extends TypeHandlerBasic {
    TypeHandlerOptional(List<BuilderCodegenExtension> extensions, PrototypeInfo prototypeInfo, OptionInfo option) {
        super(extensions, prototypeInfo, option, firstTypeArgument(option));
    }

    @Override
    public void fields(ClassBase.Builder<?, ?> classBuilder, boolean isBuilder) {
        Field.Builder builder = Field.builder()
                .isFinal(!isBuilder)
                .name(option().name());

        TypeName usedType = isBuilder ? type() : option().declaredType();

        if (isBuilder && (option().required() || option().defaultValue().isEmpty())) {
            // we need to use object types to be able to see if this was configured
            builder.type(usedType.boxed());
        } else {
            builder.type(usedType);
        }

        if (isBuilder && option().defaultValue().isPresent()) {
            option().defaultValue().get().accept(builder);
        }

        classBuilder.addField(builder);
    }

    @Override
    public void generateFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        if (optionalMap()) {
            generateOptionalMapFromConfig(method, optionConfigured);
            return;
        }
        if (optionalCollection()) {
            generateOptionalCollectionFromConfig(method, optionConfigured);
            return;
        }
        super.generateFromConfig(method, optionConfigured);
    }

    @Override
    GeneratedMethod prepareBuilderSetter(Javadoc getterJavadoc) {
        if (!optionalContainer()) {
            return super.prepareBuilderSetter(getterJavadoc);
        }

        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(containerParameterType())
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");");
            option().decorator()
                    .ifPresent(decorator -> {
                        it.addContent("new ")
                                .addContent(decorator)
                                .addContent("().decorate(this, ");
                        optionalDecoratorValue(it);
                        it.addContentLine(");");
                    });

            it.addContent("this.")
                    .addContent(name)
                    .addContent(" = ");
            mutableContainer(it, name);
            it.addContentLine(";")
                    .addContentLine("return self();");
        };

        return GeneratedMethod.builder()
                .method(method.build())
                .javadoc(setterJavadoc(getterJavadoc, name, ""))
                .contentBuilder(contentConsumer)
                .build();
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderSetterDeclared(Javadoc getterJavadoc) {
        if (optionalContainer()) {
            return prepareOptionalContainerSetterDeclared(getterJavadoc);
        }

        TypeName typeName = asTypeArgument(TypeNames.OPTIONAL);
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();
        boolean generic = typeName.typeArguments().getFirst().wildcard();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        if (generic) {
            method.addAnnotation(Annotation.create(SuppressWarnings.class, "unchecked"));
        }

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeName)
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            if (!typeName.primitive()) {
                it.addContent(Objects.class)
                        .addContentLine(".requireNonNull(" + name + ");");
            }

            it.addContent("this." + name + " = " + name);
            if (generic) {
                it.addContent(".map(")
                        .addContent(type().genericTypeName())
                        .addContent(".class::cast)");
            }

            it.addContent(".orElse(this.")
                    .addContent(option().name())
                    .addContentLine(");")
                    .addContentLine("return self();");
        };

        return Optional.ofNullable(GeneratedMethod.builder()
                                           .method(method.build())
                                           .javadoc(setterJavadoc(getterJavadoc, name, ""))
                                           .contentBuilder(contentConsumer)
                                           .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderAddCollection(Javadoc getterJavadoc) {
        if (!optionalContainer()) {
            return Optional.empty();
        }

        TypeName returnType = Utils.builderReturnType();
        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("add" + capitalize(option().name()))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(containerParameterType())
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");");
            option().decorator()
                    .ifPresent(decorator -> {
                        it.addContent("new ")
                                .addContent(decorator)
                                .addContent("().decorate(this, ");
                        optionalDecoratorValue(it);
                        it.addContentLine(");");
                    });

            it.addContentLine("if (this." + name + " == null) {")
                    .addContent("this.")
                    .addContent(name)
                    .addContent(" = ");
            emptyMutableContainer(it);
            it.addContentLine(";")
                    .addContentLine("}");

            if (optionalMap()) {
                it.addContentLine("this." + name + ".putAll(" + name + ");");
            } else {
                it.addContentLine("this." + name + ".addAll(" + name + ");");
            }
            it.addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(setterJavadoc(getterJavadoc, name, "add values to "))
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    @Override
    Optional<GeneratedMethod> prepareBuilderClear(Javadoc getterJavadoc) {
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        Javadoc javadoc = Javadoc.builder(setterJavadoc(getterJavadoc, "ignore", ""))
                .content(List.of("Clear existing value of " + name + "."))
                .parameters(Map.of())
                .build();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(option().accessModifier())
                .typeName(returnType)
                .elementName("clear" + capitalize(option().name()))
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        Consumer<ContentBuilder<?>> contentConsumer = it -> {

            option().decorator()
                    .ifPresent(decorator -> it.addContent("new ")
                            .addContent(decorator)
                            .addContent("().decorate(this, ")
                            .addContent(Optional.class)
                            .addContentLine(".empty());"));

            it.addContentLine("this." + name + " = null;")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(javadoc)
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    void decorateValue(ContentBuilder<?> contentBuilder, String optionName) {
        contentBuilder.addContent(TypeNames.OPTIONAL)
                .addContent(".of(")
                .addContent(optionName)
                .addContent(")");
    }

    private Optional<GeneratedMethod> prepareOptionalContainerSetterDeclared(Javadoc getterJavadoc) {
        TypeName typeName = asTypeArgument(TypeNames.OPTIONAL);
        TypeName returnType = Utils.builderReturnType();

        String name = option().name();

        var method = TypedElementInfo.builder()
                .kind(ElementKind.METHOD)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .typeName(returnType)
                .elementName(option().setterName())
                .update(this::deprecation)
                .update(it -> option().annotations().forEach(it::addAnnotation));

        method.addParameterArgument(param -> param
                .kind(ElementKind.PARAMETER)
                .typeName(typeName)
                .elementName(name)
        );

        Consumer<ContentBuilder<?>> contentConsumer = it -> {
            it.addContent(Objects.class)
                    .addContentLine(".requireNonNull(" + name + ");")
                    .addContent(name)
                    .addContent(".ifPresent(it -> this.")
                    .addContent(name)
                    .addContent(" = ");
            mutableContainer(it, "it");
            it.addContentLine(");")
                    .addContentLine("return self();");
        };

        return Optional.of(GeneratedMethod.builder()
                                   .method(method.build())
                                   .javadoc(setterJavadoc(getterJavadoc, name, ""))
                                   .contentBuilder(contentConsumer)
                                   .build());
    }

    private void generateOptionalCollectionFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        TypeName actualType = type().typeArguments().getFirst().genericTypeName();
        String setterName = option().setterName();

        Optional<FactoryMethod> factoryMethod = optionConfigured.factoryMethod();
        if (factoryMethod.isPresent()) {
            var fm = factoryMethod.get();
            TypeName returnType = fm.returnType();

            boolean mapList;
            if (returnType.isList() || returnType.isSet()) {
                mapList = false;
            } else {
                mapList = Utils.typesEqual(actualType, returnType);
            }
            if (mapList) {
                method.addContent(configGet(optionConfigured))
                        .addContent(".asList(")
                        .update(it -> generateMapListFromConfig(it, fm))
                        .addContent(")");
                collectionConfigMapper(method);
                method.addContentLine(".ifPresent(this::" + setterName + ");");
            } else {
                method.addContent(configGet(optionConfigured));
                generateFromConfig(method, fm.returnType(), Optional.of(fm));
                collectionConfigMapper(method);
                method.addContentLine(".ifPresent(this::" + setterName + ");");
            }
        } else if (TypeHandlerCollection.BUILT_IN_MAPPERS.contains(actualType)) {
            method.addContent(configGet(optionConfigured))
                    .addContent(".asList(")
                    .addContent(actualType.genericTypeName())
                    .addContent(".class)");
            collectionConfigMapper(method);
            method.addContentLine(".ifPresent(this::" + setterName + ");");
        } else {
            method.addContent(configGet(optionConfigured)
                                      + ".asNodeList()"
                                      + ".map(nodeList -> nodeList.stream()"
                                      + ".map(cfg -> cfg");
            generateFromConfig(method, actualType, Optional.empty());
            method.addContent(".get()).");
            collectionCollector(method);
            method.addContentLine(").ifPresent(this::" + setterName + ");");
        }
    }

    private void generateOptionalMapFromConfig(Method.Builder method, OptionConfigured optionConfigured) {
        TypeName keyType = type().typeArguments().get(0);
        TypeName valueType = type().typeArguments().get(1);
        String optionName = option().name();

        if (TypeNames.STRING.equals(keyType) && TypeNames.STRING.equals(valueType)) {
            method.addContentLine(configGet(optionConfigured) + ".detach().asMap().ifPresent(this::" + optionName + ");");
        } else {
            method.addContentLine("if (" + configGet(optionConfigured) + ".exists()) {")
                    .addContent("this.")
                    .addContent(optionName)
                    .addContent(" = ");
            emptyMutableContainer(method);
            method.addContentLine(";");

            if (optionConfigured.traverse()) {
                method.addContent(configGet(optionConfigured) + ".detach().traverse().filter(")
                        .addContent(Types.COMMON_CONFIG)
                        .addContent("::hasValue).forEach(node -> "
                                            + optionName)
                        .addContent(".put(node.get(\"name\").asString().orElse(node.key().toString()), node");
                generateFromConfig(method, valueType, optionConfigured.factoryMethod());
                method.addContentLine(".get()));");
            } else {
                method.addContent(configGet(optionConfigured)
                                          + ".asNodeList().orElseGet("
                                          + List.class.getCanonicalName()
                                          + "::of).forEach(node -> "
                                          + optionName + ".put(node.get(\"name\").asString().orElse(node.name()), node");
                generateFromConfig(method, valueType, optionConfigured.factoryMethod());
                method.addContentLine(".get()));");
            }
            method.addContentLine("}");
        }
    }

    private void generateFromConfig(ContentBuilder<?> content,
                                    TypeName usedType,
                                    Optional<FactoryMethod> factoryMethod) {
        if (factoryMethod.isPresent()) {
            FactoryMethod fm = factoryMethod.get();
            content.addContent(".as(")
                    .addContent(fm.declaringType().genericTypeName())
                    .addContent("::");
            if (!usedType.typeArguments().isEmpty()) {
                content.addContent("<");
                var iterator = usedType.typeArguments().iterator();
                while (iterator.hasNext()) {
                    content.addContent(iterator.next());
                    if (iterator.hasNext()) {
                        content.addContent(", ");
                    }
                }
                content.addContent(">");
            }
            content.addContent(fm.methodName())
                    .addContent(")");
            return;
        }

        if (usedType.fqName().equals("char[]")) {
            content.addContent(".asString().as(")
                    .addContent(String.class)
                    .addContent("::toCharArray)");
            return;
        }

        if (usedType.equals(TypeNames.STRING)) {
            content.addContent(".asString()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_INT)) {
            content.addContent(".asInt()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_DOUBLE)) {
            content.addContent(".asDouble()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_BOOLEAN)) {
            content.addContent(".asBoolean()");
            return;
        }

        if (usedType.boxed().equals(TypeNames.BOXED_LONG)) {
            content.addContent(".asLong()");
            return;
        }

        content.addContent(".as(")
                .addContent(usedType.boxed().genericTypeName())
                .addContent(".class)");
    }

    private void generateMapListFromConfig(ContentBuilder<?> content, FactoryMethod factoryMethod) {
        var declaringType = factoryMethod.declaringType();
        var methodName = factoryMethod.methodName();

        content.addContent(declaringType.genericTypeName())
                .addContent("::")
                .addContent(methodName);
    }

    private boolean optionalContainer() {
        return optionalCollection() || optionalMap();
    }

    private boolean optionalCollection() {
        return type().isList() || type().isSet();
    }

    private boolean optionalMap() {
        return type().isMap();
    }

    private TypeName containerParameterType() {
        if (optionalMap()) {
            return TypeName.builder(MAP)
                    .addTypeArgument(Utils.toWildcard(type().typeArguments().get(0)))
                    .addTypeArgument(Utils.toWildcard(type().typeArguments().get(1)))
                    .build();
        }
        return TypeName.builder(collectionType())
                .addTypeArgument(Utils.toWildcard(type().typeArguments().getFirst()))
                .build();
    }

    private TypeName collectionType() {
        return type().isList() ? LIST : SET;
    }

    private void collectionConfigMapper(ContentBuilder<?> content) {
        if (type().isSet()) {
            content.addContent(".map(")
                    .addContent(LinkedHashSet.class)
                    .addContent("::new)");
        }
    }

    private void collectionCollector(ContentBuilder<?> content) {
        if (type().isList()) {
            content.addContent("toList()");
        } else {
            content.addContent("collect(")
                    .addContent(Collectors.class)
                    .addContent(".toCollection(")
                    .addContent(LinkedHashSet.class)
                    .addContent("::new))");
        }
    }

    private void optionalDecoratorValue(ContentBuilder<?> content) {
        content.addContent(Optional.class)
                .addContent(".of(");
        immutableContainer(content, option().name());
        content.addContent(")");
    }

    private void mutableContainer(ContentBuilder<?> content, String source) {
        content.addContent("new ");
        if (type().isList()) {
            content.addContent(ArrayList.class);
        } else if (type().isSet()) {
            content.addContent(LinkedHashSet.class);
        } else {
            content.addContent(LinkedHashMap.class);
        }
        content.addContent("<>(")
                .addContent(source)
                .addContent(")");
    }

    private void emptyMutableContainer(ContentBuilder<?> content) {
        content.addContent("new ");
        if (type().isList()) {
            content.addContent(ArrayList.class);
        } else if (type().isSet()) {
            content.addContent(LinkedHashSet.class);
        } else {
            content.addContent(LinkedHashMap.class);
        }
        content.addContent("<>()");
    }

    private void immutableContainer(ContentBuilder<?> content, String source) {
        if (type().isList()) {
            content.addContent(List.class)
                    .addContent(".copyOf(")
                    .addContent(source)
                    .addContent(")");
        } else if (type().isSet()) {
            content.addContent(Collections.class)
                    .addContent(".unmodifiableSet(new ")
                    .addContent(LinkedHashSet.class)
                    .addContent("<>(")
                    .addContent(source)
                    .addContent("))");
        } else {
            content.addContent(Collections.class)
                    .addContent(".unmodifiableMap(new ")
                    .addContent(LinkedHashMap.class)
                    .addContent("<>(")
                    .addContent(source)
                    .addContent("))");
        }
    }
}
