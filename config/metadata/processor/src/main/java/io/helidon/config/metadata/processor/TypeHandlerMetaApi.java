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

package io.helidon.config.metadata.processor;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypeValues;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.config.metadata.processor.ConfiguredType.ProducerMethod;

import static io.helidon.config.metadata.processor.UsedTypes.COMMON_CONFIG;
import static io.helidon.config.metadata.processor.UsedTypes.CONFIG;
import static io.helidon.config.metadata.processor.UsedTypes.META_CONFIGURED;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTION;
import static io.helidon.config.metadata.processor.UsedTypes.META_OPTIONS;

/*
 * Takes care of blueprints annotated with builder API only.
 */
class TypeHandlerMetaApi extends TypeHandlerMetaApiBase implements TypeHandler {
    private final TypeInfo typeInfo;
    private final TypeName typeName;

    TypeHandlerMetaApi(ProcessingEnvironment aptEnv, TypeInfo typeInfo) {
        super(aptEnv);

        this.typeInfo = typeInfo;
        this.typeName = typeInfo.typeName();
    }

    @Override
    public TypeHandlerResult handle() {
        TypeInfo targetType;
        boolean isBuilder;
        String module;
        Optional<TypeName> foundTarget = findBuilderTarget(new HashSet<>(), typeInfo);
        ConfiguredAnnotation configured = ConfiguredAnnotation.createMeta(typeInfo.annotation(META_CONFIGURED));
        if (!configured.ignoreBuildMethod()
                && !typeInfo.modifiers().contains(TypeValues.MODIFIER_ABSTRACT)
                && foundTarget.isPresent()) {
            // this is a builder, we need the target type Builder<Builder, TargetType>
            TypeName targetTypeName = foundTarget.get();
            targetType = typeInfo(targetTypeName, ElementInfoPredicates::isMethod)
                    .orElseThrow(() -> new IllegalStateException("Cannot find target type info for type "
                                                                         + targetTypeName.fqName()
                    + ", discovered for type: " + typeInfo.typeName().fqName()));
            isBuilder = true;
            module = targetType.module().orElse("unknown");
        } else {
            targetType = typeInfo;
            isBuilder = false;
            module = typeInfo.module().orElse("unknown");
        }

        /*
          now we know whether this is
          - a builder + known target class (result of builder() method)
          - a standalone class (probably with public static create(Config) method)
          - an interface/abstract class only used for inheritance
         */
        ConfiguredType type = new ConfiguredType(configured,
                                                 typeName,
                                                 targetType.typeName(),
                                                 false);

        /*
         we also need to know all superclasses / interfaces that are configurable so we can reference them
         these may be from other modules, so we cannot create a single set of values from all types
         */
        addSuperClasses(type, typeInfo, META_CONFIGURED);
        addInterfaces(type, typeInfo, META_CONFIGURED);

        if (isBuilder) {
            // builder
            processBuilderType(typeInfo, type, typeName, targetType);
        } else {
            // standalone class with create method(s), or interface/abstract class
            processTargetType(typeInfo, type, typeName, type.standalone());
        }

        return new TypeHandlerResult(targetType.typeName(), module, type);
    }

    // annotated type or type methods (not a builder)
    private void processTargetType(TypeInfo typeInfo, ConfiguredType type, TypeName typeName, boolean standalone) {
        // go through all methods, find all create methods and create appropriate configured producers for them
        // if there is a builder, add the builder producer as well

        List<TypedElementInfo> methods = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                // public, package local or protected
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                // static
                .filter(ElementInfoPredicates::isStatic)
                .toList();

        // either this is a target class (such as an interface with create method)
        // or this is an interface/abstract class inherited by builders
        boolean isTargetType = false;
        List<TypedElementInfo> validMethods = new LinkedList<>();
        TypedElementInfo configCreator = null;

        // now we have just public static methods, let's look for create/builder
        for (TypedElementInfo method : methods) {
            String name = method.elementName();

            if ("create".equals(name)) {
                if (method.typeName().genericTypeName().equals(typeName.genericTypeName())) {
                    validMethods.add(method);
                    List<TypedElementInfo> parameters = method.parameterArguments();
                    if (parameters.size() == 1) {
                        TypeName paramType = parameters.get(0).typeName();
                        if (paramType.equals(CONFIG) || paramType.equals(COMMON_CONFIG)) {
                            configCreator = method;
                        }
                    }
                    isTargetType = true;
                }
            } else if (name.equals("builder")) {
                aptMessager().printMessage(Diagnostic.Kind.ERROR, "Type " + typeName.fqName() + " is marked with @Configured"
                                                   + ", yet it has a static builder() method. Please mark the builder instead "
                                                   + "of this class.",
                                           aptElements().getTypeElement(typeName.fqName()));
            }
        }

        if (isTargetType) {
            if (configCreator != null) {
                type.addProducer(new ProducerMethod(true,
                                                    typeName,
                                                    configCreator.elementName(),
                                                    params(configCreator)));
            }

            // now let's find all methods with @ConfiguredOption
            for (TypedElementInfo validMethod : validMethods) {
                List<ConfiguredOptionData> options = findConfiguredOptionAnnotations(validMethod);

                if (options.isEmpty()) {
                    continue;
                }

                for (ConfiguredOptionData data : options) {
                    if ((data.name() == null || data.name().isBlank()) && !data.merge()) {
                        TypeElement typeElement = aptElements().getTypeElement(typeName.fqName());
                        aptMessager().printMessage(Diagnostic.Kind.ERROR,
                                                   "ConfiguredOption on " + typeElement + "."
                                                           + validMethod
                                                           + " does not have value defined. It is mandatory on non-builder "
                                                           + "methods",
                                                   typeElement);
                        return;
                    }

                    if (data.description() == null || data.description().isBlank()) {
                        TypeElement typeElement = aptElements().getTypeElement(typeName.fqName());
                        aptMessager().printMessage(Diagnostic.Kind.ERROR,
                                                   "ConfiguredOption on " + typeElement + "." + validMethod
                                                           + " does not have description defined. It is mandatory on non-builder "
                                                           + "methods",
                                                   typeElement);
                        return;
                    }

                    if (data.type() == null) {
                        // this is the default value
                        data.type(TypeNames.STRING);
                    }

                    ConfiguredType.ConfiguredProperty prop = new ConfiguredType.ConfiguredProperty(null,
                                                                                                   data.name(),
                                                                                                   data.description(),
                                                                                                   data.defaultValue(),
                                                                                                   data.type(),
                                                                                                   data.experimental(),
                                                                                                   data.optional(),
                                                                                                   data.kind(),
                                                                                                   data.provider(),
                                                                                                   data.providerType(),
                                                                                                   data.deprecated(),
                                                                                                   data.merge(),
                                                                                                   data.allowedValues());
                    type.addProperty(prop);
                }
            }
        } else {
            // this must be a class/interface used by other classes to extend, so we care about all builder style
            // methods
            if (standalone) {
                aptMessager().printMessage(Diagnostic.Kind.ERROR,
                                           "Type " + typeName.fqName() + " is marked as standalone configuration unit, "
                                                   + "yet it does have "
                                                   + "neither a builder method, nor a create method");
                return;
            }

            typeInfo.elementInfo()
                    .stream()
                    .filter(ElementInfoPredicates::isMethod) // methods
                    .filter(Predicate.not(ElementInfoPredicates::isPrivate)) // public, package or protected
                    .filter(Predicate.not(ElementInfoPredicates::isStatic)) // not static
                    .filter(TypeHandlerMetaApiBase.isMine(typeName)) // declared on this type
                    .forEach(it -> processBuilderMethod(typeName, type, it));
        }
    }

    // annotated builder methods
    private void processBuilderType(TypeInfo typeInfo, ConfiguredType type, TypeName typeName, TypeInfo targetType) {
        type.addProducer(new ProducerMethod(false, typeName, "build", List.of()));

        TypeName targetTypeName = targetType.typeName();
        // check if static TargetType create(Config) exists
        if (targetType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates.elementName("create"))
                .filter(TypeHandlerMetaApiBase::hasConfigParam)
                .anyMatch(TypeHandlerMetaApiBase.isMine(targetTypeName))) {

            type.addProducer(new ProducerMethod(true,
                                                targetTypeName,
                                                "create",
                                                List.of(COMMON_CONFIG)));
        }

        // find all public methods annotated with @ConfiguredOption
        typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod) // methods
                .filter(Predicate.not(ElementInfoPredicates::isPrivate)) // not private
                .filter(TypeHandlerMetaApiBase.isMine(typeName)) // declared on this type
                .filter(it -> it.hasAnnotation(META_OPTION) || it.hasAnnotation(META_OPTIONS))
                .forEach(it -> processBuilderMethod(typeName, type, it));
    }

    private List<TypeName> builderMethodParams(TypedElementInfo elementInfo, OptionType type) {
        return params(elementInfo);
    }

    private void processBuilderMethod(TypeName typeName, ConfiguredType configuredType, TypedElementInfo elementInfo) {
        processBuilderMethod(typeName, configuredType, elementInfo, this::optionType, this::builderMethodParams);
    }

    private OptionType optionType(TypedElementInfo elementInfo, ConfiguredOptionData annotation) {
        if (annotation.type() == null || annotation.type().equals(META_OPTION)) {
            // guess from method

            List<TypedElementInfo> parameters = elementInfo.parameterArguments();
            if (parameters.size() != 1) {
                aptMessager().printMessage(Diagnostic.Kind.ERROR, "Method " + elementInfo.elementName()
                                                   + " is annotated with @ConfiguredOption, "
                                                   + "yet it does not have explicit type, or exactly one parameter",
                                           aptElements().getTypeElement(elementInfo.enclosingType().map(TypeName::fqName)
                                                                                .orElse(null)));
                return new OptionType(TypeNames.STRING, "VALUE");
            } else {
                TypedElementInfo parameter = parameters.iterator().next();
                TypeName paramType = parameter.typeName();

                if (paramType.isList() || paramType.isSet()) {
                    return new OptionType(paramType.typeArguments().get(0), "LIST");
                }

                if (paramType.isMap()) {
                    return new OptionType(paramType.typeArguments().get(1), "MAP");
                }

                return new OptionType(paramType.boxed(), annotation.kind());
            }

        } else {
            // use the one defined on annotation
            return new OptionType(annotation.type(), annotation.kind());
        }
    }

    private Optional<TypeName> findBuilderTarget(Set<TypeName> processed, TypeInfo typeInfo) {
        // non-private build method exists that has no parameters, not static, and returns a type
        return typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(ElementInfoPredicates.elementName("build"))
                .filter(Predicate.not(ElementInfoPredicates::isVoid))
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .findFirst()
                .map(it -> it.typeName());
    }
}
