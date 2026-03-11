/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.JavadocReader;
import io.helidon.codegen.JavadocWriter;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static java.util.function.Predicate.not;

class SchemaGenerator {

    private final CodegenContext ctx;
    private final TypeResolver resolver;
    private final CodegenLogger logger;

    SchemaGenerator(CodegenContext ctx) {
        this.ctx = ctx;
        this.logger = ctx.logger();
        this.resolver = new TypeResolver(ctx);
    }

    Annotation type(PrototypeInfo prototypeInfo, List<OptionHandler> handlers) {
        var builder = Annotation.builder().typeName(Types.CONFIGURED);
        var configured = prototypeInfo.configured().orElseThrow();
        var key = configured.key().orElse("");
        if (!key.isBlank()) {
            if (configured.root()) {
                builder.property("root", true);
            }
            builder.property("prefix", key);
        }

        var description = typeDescription(prototypeInfo);
        builder.property("description", description);

        var provides = providedTypes(prototypeInfo);
        if (!provides.isEmpty()) {
            builder.property("provides", provides);
        }

        var options = options(prototypeInfo, handlers);
        if (!options.isEmpty()) {
            builder.property("options", options);
        }
        return builder.build();
    }

    private List<TypeName> providedTypes(PrototypeInfo prototypeInfo) {
        var enclosingTypeInfo = prototypeInfo.blueprint();
        var provides = new ArrayList<TypeName>();
        for (var e : prototypeInfo.providerProvides()) {
            var providerTypeInfo = ctx.typeInfo(e).orElse(null);
            if (providerTypeInfo != null) {
                var providedTypeName = providedTypeName(providerTypeInfo);
                if (providedTypeName != null) {
                    provides.add(providedTypeName);
                } else {
                    logger.log(Level.WARNING, "Unresolved configured provider type: " + e.fqName(), enclosingTypeInfo);
                }
            } else {
                logger.log(Level.WARNING, "Unresolved type: " + e.fqName(), enclosingTypeInfo);
            }
        }
        // check Prototype.Factory<? extends NamedService> declares provider
        var rtName = prototypeInfo.runtimeType().orElse(null);
        if (rtName != null) {
            var rtInfo = ctx.typeInfo(rtName).orElseThrow(() ->
                    new CodegenException("Unable to resolve: " + rtName.fqName(), enclosingTypeInfo));
            if (resolver.isSubtype(rtInfo, Types.NAMED_SERVICE)) {
                if (provides.isEmpty()) {
                    logger.log(Level.WARNING, "Configured provider not declared for: " + rtName.fqName(), enclosingTypeInfo);
                }
            }
        }
        return provides;
    }

    private TypeName providedTypeName(TypeInfo providedTypeInfo) {
        for (var superTypeInfo : resolver.typeHierarchy(providedTypeInfo)) {
            var superTypeName = superTypeInfo.typeName();
            var rawSuperTypeName = superTypeName.genericTypeName();
            if (rawSuperTypeName.equals(Types.CONFIGURED_PROVIDER)) {
                for (var typeArg : superTypeName.typeArguments()) {
                    var resolvedTypeArgInfo = ctx.typeInfo(typeArg)
                            .orElseGet(() -> resolver.resolveTypeParameter(typeArg, superTypeName));
                    if (resolvedTypeArgInfo != null) {
                        return resolvedTypeArgInfo.typeName();
                    }
                }
                return null;
            }
        }
        return providedTypeInfo.typeName();
    }

    private List<Annotation> options(PrototypeInfo prototypeInfo, List<OptionHandler> handlers) {
        var options = new ArrayList<Annotation>();
        for (var handler : handlers) {
            var optionInfo = handler.option();
            if (optionInfo.configured().isPresent()) {
                options.add(option(prototypeInfo, optionInfo));
            }
        }
        return options;
    }

    private Annotation option(PrototypeInfo prototypeInfo, OptionInfo optionInfo) {
        var configured = optionInfo.configured().orElseThrow();
        var builder = Annotation.builder().typeName(Types.CONFIGURED_OPTION);

        var key = configured.configKey();
        var description = optionDescription(prototypeInfo, optionInfo);

        var typeName = optionTypeName(optionInfo);
        var typeInfo = optionTypeInfo(prototypeInfo, optionInfo, typeName);

        if (configured.merge()) {
            if (typeName.equals(TypeNames.STRING) || typeName.unboxed().primitive()) {
                var methodInfo = optionInfo.interfaceMethod().orElse(null);
                logger.log(Level.WARNING, "Invalid merge option type: " + typeName.fqName(), methodInfo);
            } else {
                builder.property("type", typeName);
                builder.property("mergeWithParent", true);
                return builder.build();
            }
        }

        builder.property("key", key);
        builder.property("description", description);
        builder.property("type", typeName);

        var kind = optionKind(optionInfo);
        if (!"VALUE" .equals(kind)) {
            var enumValue = EnumValue.create(Types.CONFIGURED_OPTION_KIND, kind);
            builder.property("kind", enumValue);
        }

        var defaultValue = defaultValue(optionInfo);
        if (defaultValue != null) {
            builder.property("value", defaultValue);
        }

        if (optionInfo.required()) {
            builder.property("required", true);
        }

        if (optionInfo.provider().isPresent()) {
            builder.property("provider", true);
        }

        if (optionInfo.deprecation().isPresent()) {
            builder.property("deprecated", true);
        }

        var allowedValues = allowedValues(typeInfo, optionInfo);
        if (!allowedValues.isEmpty()) {
            builder.property("allowedValues", allowedValues);
        }
        return builder.build();
    }

    private String defaultValue(OptionInfo optionInfo) {
        var key = optionInfo.configured().orElseThrow().configKey();
        if (key.endsWith("-discover-services")) {
            return optionInfo.interfaceMethod()
                    .flatMap(it -> it.findAnnotation(Types.OPTION_PROVIDER))
                    .flatMap(a -> a.booleanValue("discoverServices"))
                    .map(String::valueOf)
                    .orElse("true");
        } else {
            return optionInfo.interfaceMethod()
                    .flatMap(it -> it.findAnnotation(Types.OPTION_DEFAULT)
                            .or(() -> it.findAnnotation(Types.OPTION_DEFAULT_INT))
                            .or(() -> it.findAnnotation(Types.OPTION_DEFAULT_BOOLEAN))
                            .or(() -> it.findAnnotation(Types.OPTION_DEFAULT_LONG))
                            .or(() -> it.findAnnotation(Types.OPTION_DEFAULT_DOUBLE))
                            .flatMap(Annotation::stringValues))
                    .map(it -> String.join(", ", it))
                    .orElse(null);
        }
    }

    private List<Annotation> allowedValues(TypeInfo typeInfo, OptionInfo optionInfo) {
        if (!optionInfo.allowedValues().isEmpty()) {
            return allowedValues(optionInfo.allowedValues());
        } else if (typeInfo != null && typeInfo.kind() == ElementKind.ENUM) {
            var methodInfo = optionInfo.interfaceMethod().orElse(null);
            return allowedValues(typeInfo, methodInfo, typeInfo);
        } else {
            return List.of();
        }
    }

    private List<Annotation> allowedValues(List<OptionAllowedValue> allowedValues) {
        var result = new ArrayList<Annotation>();
        for (var e : allowedValues) {
            result.add(Annotation.builder()
                    .typeName(Types.CONFIGURED_VALUE)
                    .property("value", e.value())
                    .property("description", e.description())
                    .build());
        }
        return result;
    }

    private List<Annotation> allowedValues(TypeInfo typeInfo, Object... originElements) {
        var result = new ArrayList<Annotation>();
        for (var e : typeInfo.elementInfo()) {
            if (e.kind() == ElementKind.ENUM_CONSTANT) {
                var value = e.elementName();
                var description = e.description()
                        .map(SchemaGenerator::javadoc)
                        .filter(not(String::isBlank))
                        .orElse(null);
                if (description == null) {
                    logger.log(Level.WARNING,
                            "Missing javadoc: %s.%s" .formatted(e.typeName().fqName(), e.elementName()),
                            originElements);
                    description = "<code>N/A</code>";
                }
                result.add(Annotation.builder()
                        .typeName(Types.CONFIGURED_VALUE)
                        .property("value", value)
                        .property("description", description)
                        .build());
            }
        }
        return result;
    }

    private String optionDescription(PrototypeInfo prototypeInfo, OptionInfo optionInfo) {
        var key = optionInfo.configured().orElseThrow().configKey();
        if (key.endsWith("-discover-services")) {
            var providerKey = key.substring(0, key.length() - "-discover-services" .length());
            return "Whether to enable automatic service discovery for <code>" + providerKey + "</code>";
        } else {
            var description = optionInfo.description()
                    .map(SchemaGenerator::javadoc)
                    .filter(not(String::isBlank))
                    .orElse(null);
            if (description != null) {
                return javadoc(description);
            } else {
                var typeInfo = prototypeInfo.blueprint();
                var methodInfo = optionInfo.interfaceMethod().orElse(null);
                if (methodInfo != null) {
                    return methodDescription(prototypeInfo, methodInfo);
                } else {
                    throw new CodegenException("Unable to resolve description for option: " + key, typeInfo);
                }
            }
        }
    }

    private String methodDescription(PrototypeInfo prototypeInfo, TypedElementInfo methodInfo) {
        var description = methodInfo.description()
                .map(SchemaGenerator::javadoc)
                .filter(not(String::isBlank))
                .orElse(null);
        if (description != null) {
            return javadoc(description);
        } else {
            var typeInfo = prototypeInfo.blueprint();
            for (TypedElementInfo e : resolver.methodHierarchy(typeInfo, methodInfo)) {
                description = e.description()
                        .map(SchemaGenerator::javadoc)
                        .filter(not(String::isBlank))
                        .orElse(null);
                if (description != null) {
                    return javadoc(description);
                }
            }
            logger.log(Level.WARNING, "Missing javadoc", methodInfo, typeInfo);
            return "<code>N/A</code>";
        }
    }

    private String typeDescription(PrototypeInfo prototypeInfo) {
        var typeInfo = prototypeInfo.blueprint();
        var description = typeInfo.description()
                .or(() -> prototypeInfo.runtimeType()
                        .flatMap(ctx::typeInfo)
                        .flatMap(TypeInfo::description))
                .filter(not(String::isBlank))
                .map(SchemaGenerator::javadoc)
                .orElse(null);
        if (description == null) {
            logger.log(Level.WARNING, "Missing javadoc", typeInfo);
            description = "<code>N/A</code>";
        }
        return description;
    }

    private TypeInfo optionTypeInfo(PrototypeInfo prototypeInfo, OptionInfo optionInfo, TypeName typeName) {
        if (typeName.equals(TypeNames.OBJECT)) {
            return null;
        }
        var typeInfo = prototypeInfo.blueprint();
        var methodInfo = optionInfo.interfaceMethod().orElse(null);
        var lookup = typeName.genericTypeName().boxed();
        var lookupName = lookup.fqName();
        return ctx.typeInfo(lookup)
                .or(() -> {
                    var className = lookup.classNameWithEnclosingNames();
                    var blueprintName = className.replace(".Builder", "") + "Blueprint";
                    return ctx.typeInfo(TypeName.builder(typeInfo.typeName())
                            .className(blueprintName)
                            .build());
                })
                .orElseThrow(() -> new CodegenException(
                        "Unable to resolve: " + lookupName, methodInfo, typeInfo));
    }

    private boolean isPrototyped(TypeName typeName) {
        return ctx.typeInfo(typeName)
                .map(it -> resolver.isSubtype(it, Types.RUNTIME_API))
                .orElse(false);
    }

    private TypeName optionTypeName(OptionInfo optionInfo) {
        var typeName = optionInfo.declaredType();
        if (typeName.isOptional()) {
            typeName = typeName.typeArguments().getFirst();
        }
        if (typeName.equals(Types.CHAR_ARRAY)) {
            return TypeNames.STRING;
        }
        if (typeName.isSet() || typeName.isList()) {
            typeName = typeName.typeArguments().getFirst();
        } else if (typeName.isMap()) {
            typeName = typeName.typeArguments().get(1);
        }

        if (optionInfo.prototypedBy().isPresent()) {
            return optionInfo.prototypedBy().get();
        }

        // check configured factory method
        var configuredDeclaredTypeName = optionInfo.configured()
                .flatMap(OptionConfigured::factoryMethod)
                .map(FactoryMethod::declaringType)
                .orElse(null);
        if (configuredDeclaredTypeName != null) {
            return configuredDeclaredTypeName;
        }

        // check runtime factory method
        var runtimeParamTypeName = optionInfo.runtimeType()
                .flatMap(RuntimeTypeInfo::factoryMethod)
                .flatMap(FactoryMethod::parameterType)
                .orElse(null);
        if (runtimeParamTypeName != null) {
            return runtimeParamTypeName;
        }

        return typeName.boxed();
    }

    private String optionKind(OptionInfo optionInfo) {
        var typeName = optionInfo.declaredType();
        if (typeName.isOptional()) {
            typeName = typeName.typeArguments().getFirst();
        }
        if (typeName.isList() || typeName.isSet()) {
            return "LIST";
        } else if (typeName.isMap()) {
            return "MAP";
        } else {
            return "VALUE";
        }
    }

    private static String javadoc(String description) {
        var reader = JavadocReader.create(description);
        var document = reader.read();
        var buf = new StringBuilder();
        var writer = JavadocWriter.create(buf);
        writer.write(document.firstSentence());

        // remove new lines and the trailing dot
        for (int i = 0, len = buf.length(), offset = 0; i < len; i++) {
            int index = i - offset;
            char c = buf.charAt(index);
            if (c == '\n' || (i + 1 == len && c == '.')) {
                buf.deleteCharAt(index);
                offset++;
            }
        }
        return buf.toString();
    }
}
