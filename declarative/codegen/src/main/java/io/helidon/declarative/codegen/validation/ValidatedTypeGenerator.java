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

package io.helidon.declarative.codegen.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addTypeValidator;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfConstraint;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfTypeArguments;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfValid;
import static io.helidon.declarative.codegen.validation.ValidationHelper.needsWork;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VIOLATION_LOCATION;
import static io.helidon.declarative.codegen.validation.ValidationTypes.TYPE_VALIDATOR;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_CONTEXT;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_EXCEPTION;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALID;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALIDATED;

class ValidatedTypeGenerator {
    private static final TypeName GENERATOR = TypeName.create(ValidatedTypeGenerator.class);
    private final RegistryRoundContext roundContext;
    private final Collection<TypeName> constraintAnnotations;

    ValidatedTypeGenerator(RegistryRoundContext roundContext, Collection<TypeName> constraintAnnotations) {
        this.roundContext = roundContext;
        this.constraintAnnotations = constraintAnnotations;
    }

    void process(TypeInfo typeInfo) {
        // a type to be validated, may contain properties that either have constraints, or are @Valid

        TypeName triggerType = typeInfo.typeName();
        var generatedType = TypeName.builder()
                .packageName(triggerType.packageName())
                .className(triggerType.classNameWithEnclosingNames().replace('.', '_') + "__Validated")
                .build();

        var classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, triggerType, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, triggerType, generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.builder()
                                       .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED_BY_TYPE)
                                       .putValue("value", triggerType)
                                       .build())
                .addInterface(TypeName.builder()
                                      .from(TYPE_VALIDATOR)
                                      .addTypeArgument(triggerType)
                                      .build());

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        FieldHandler fieldHandler = FieldHandler.create(classModel, constructor);
        Method.Builder checkMethod = Method.builder()
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .name("check")
                .addParameter(context -> context.name("validation__ctx")
                        .type(VALIDATION_CONTEXT))
                .addParameter(instance -> instance.name("validation__instance")
                        .type(triggerType))
                .addContentLine();

        // now we can add all the fields necessary to validate this type, within the type scope
        checkMethod.addContent("try (var validation__typeScope = validation__ctx.scope(")
                .addContent(CONSTRAINT_VIOLATION_LOCATION)
                .addContent(".TYPE, ")
                .addContent(triggerType)
                .addContentLine(".class.getName())) {")
                .addContentLine();

        if (typeInfo.kind() == ElementKind.RECORD) {
            // handle record elements
            processValidatedRecord(generatedType, constraintAnnotations, typeInfo, classModel, fieldHandler, checkMethod);
        } else if (typeInfo.kind() == ElementKind.CLASS) {
            // handle class fields and methods to discover properties
            processValidatedClass(generatedType, constraintAnnotations, typeInfo, classModel, fieldHandler, checkMethod);
        } else if (typeInfo.kind() == ElementKind.INTERFACE) {
            // only supports getters
            processValidatedInterface(generatedType, constraintAnnotations, typeInfo, classModel, fieldHandler, checkMethod);
        } else {
            throw new CodegenException(VALIDATION_VALIDATED.fqName() + " annotation on illegal type. "
                                               + "Only record, class, or interface are currently supported.",
                                       typeInfo);
        }

        // leave from type check
        checkMethod.addContentLine("}");
        classModel.addConstructor(constructor);
        classModel.addMethod(checkMethod);

        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void processValidatedRecord(TypeName generatedType,
                                        Collection<TypeName> constraintAnnotations,
                                        TypeInfo type,
                                        ClassModel.Builder classModel,
                                        FieldHandler fieldHandler,
                                        Method.Builder checkMethod) {
        List<Property> recordComponents = new ArrayList<>();

        type.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                .filter(it -> needsWork(constraintAnnotations, it))
                .forEach(element -> {
                    String propertyName = element.elementName();
                    Property property = new Property(propertyName, "check" + capitalize(propertyName), element.typeName());
                    recordComponents.add(property);

                    checkMethod.addContent(property.checkMethodName())
                            .addContent("(validation__ctx, validation__instance.")
                            .addContent(propertyName)
                            .addContentLine("());");

                    addCheckMethod(generatedType,
                                   classModel,
                                   constraintAnnotations,
                                   fieldHandler,
                                   "RECORD_COMPONENT",
                                   element,
                                   propertyName);
                });

        checkMethod.addContentLine();

        addCheckWithPropertyNameMethods(classModel, type.typeName(), recordComponents);
    }

    private void processValidatedClass(TypeName generatedType,
                                       Collection<TypeName> constraintAnnotations,
                                       TypeInfo type,
                                       ClassModel.Builder classModel,
                                       FieldHandler fieldHandler,
                                       Method.Builder checkMethod) {

        List<Property> properties = new ArrayList<>();

        // non-private non-static methods that match getter pattern (we only add those annotated with a constraint or Valid)
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(Predicate.not(ElementInfoPredicates::isVoid))
                .filter(it -> needsWork(constraintAnnotations, it))
                .forEach(element -> {
                    String propertyName = element.elementName();
                    if (isPropertyGetter(propertyName)) {
                        propertyName = nameFromPropertyGetter(propertyName);
                    }
                    Property property = new Property(propertyName,
                                                     "check" + capitalize(propertyName),
                                                     element.typeName(),
                                                     element.elementName());

                    properties.add(property);

                    checkMethod.addContent(property.checkMethodName())
                            .addContent("(validation__ctx, validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine("());");

                    addCheckMethod(generatedType,
                                   classModel,
                                   constraintAnnotations,
                                   fieldHandler,
                                   "PROPERTY",
                                   element,
                                   propertyName);
                });

        // non-private non-static fields
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(it -> needsWork(constraintAnnotations, it))
                .forEach(element -> {
                    String propertyName = element.elementName();
                    Property property = new Property(propertyName,
                                                     "check" + capitalize(propertyName),
                                                     element.typeName(),
                                                     false);
                    properties.add(property);

                    checkMethod.addContent(property.checkMethodName())
                            .addContent("(validation__ctx, validation__instance.")
                            .addContent(propertyName)
                            .addContentLine(");");

                    addCheckMethod(generatedType,
                                   classModel,
                                   constraintAnnotations,
                                   fieldHandler,
                                   "FIELD",
                                   element,
                                   propertyName);
                });

        checkMethod.addContentLine();

        addCheckWithPropertyNameMethods(classModel, type.typeName(), properties);
    }

    private void processValidatedInterface(TypeName generatedType,
                                           Collection<TypeName> constraintAnnotations,
                                           TypeInfo type,
                                           ClassModel.Builder classModel,
                                           FieldHandler fieldHandler, Method.Builder checkMethod) {

        List<Property> properties = new ArrayList<>();

        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(Predicate.not(ElementInfoPredicates::isVoid))
                .filter(it -> needsWork(constraintAnnotations, it))
                .forEach(element -> {
                    String propertyName = element.elementName();
                    if (isPropertyGetter(propertyName)) {
                        propertyName = nameFromPropertyGetter(propertyName);
                    }
                    Property property = new Property(propertyName,
                                                     "check" + capitalize(propertyName),
                                                     element.typeName(),
                                                     element.elementName());
                    properties.add(property);

                    checkMethod.addContent(property.checkMethodName())
                            .addContent("(validation__ctx, validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine("());");

                    addCheckMethod(generatedType,
                                   classModel,
                                   constraintAnnotations,
                                   fieldHandler,
                                   "PROPERTY",
                                   element,
                                   propertyName);
                });

        checkMethod.addContentLine();

        addCheckWithPropertyNameMethods(classModel, type.typeName(), properties);
    }

    private static boolean isPropertyGetter(String propertyName) {
        // getSomething -> OK
        // getHTTPS -> OK
        // getting -> not OK
        return propertyName.startsWith("get")
                && propertyName.length() > 3
                && Character.isUpperCase(propertyName.charAt(3));
    }

    private static String nameFromPropertyGetter(String propertyName) {
        // getSomething -> Something
        // getHTTPS -> HTTPS
        // getX -> X
        propertyName = propertyName.substring(3);
        var firstChar = propertyName.charAt(0);

        if (propertyName.length() == 1) {
            // i.e. X -> x
            return String.valueOf(Character.toLowerCase(firstChar));
        } else if (!Character.isUpperCase(propertyName.charAt(1))) {
            // i.e. Something -> something
            return Character.toLowerCase(firstChar) + propertyName.substring(1);
        }
        // i.e. HTTPS -> HTTPS
        return propertyName;
    }

    private void addCheckWithPropertyNameMethods(ClassModel.Builder classModel,
                                                 TypeName triggerType,
                                                 List<Property> properties) {
        /*
        Check a single property of an instance
         */
        Method.Builder checkProperty = Method.builder()
                .name("check")
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(context -> context.name("ctx")
                        .type(VALIDATION_CONTEXT))
                .addParameter(instance -> instance.name("instance")
                        .type(triggerType))
                .addParameter(propertyName -> propertyName.name("propertyName")
                        .type(TypeNames.STRING))
                .addContentLine("switch (propertyName) {");

        for (Property property : properties) {
            checkProperty.addContent("case ")
                    .addContentLiteral(property.name())
                    .addContent(" -> ")
                    .addContent(property.checkMethodName())
                    .addContent("(ctx, (")
                    .addContent(property.type().boxed())
                    .addContent(") instance.")
                    .addContent(property.getterName())
                    .addContent(property.method() ? "()" : "")
                    .addContentLine(");");
        }
        checkProperty.addContent("default -> throw new ")
                .addContent(VALIDATION_EXCEPTION)
                .addContent("(")
                .addContentLiteral("Invalid property name: ")
                .addContentLine(" + propertyName);")
                .addContentLine("};");

        classModel.addMethod(checkProperty);

        /*
        Check a single property value
         */
        Method.Builder checkPropertyValue = Method.builder()
                .name("checkProperty")
                .accessModifier(AccessModifier.PUBLIC)
                .addAnnotation(Annotations.OVERRIDE)
                .addParameter(context -> context.name("ctx")
                        .type(VALIDATION_CONTEXT))
                .addParameter(propertyName -> propertyName.name("propertyName")
                        .type(TypeNames.STRING))
                .addParameter(value -> value.name("value")
                        .type(TypeNames.OBJECT))
                .addContentLine("switch (propertyName) {");

        for (Property property : properties) {
            checkPropertyValue.addContent("case ")
                    .addContentLiteral(property.name())
                    .addContent(" -> ")
                    .addContent(property.checkMethodName())
                    .addContent("(ctx, (")
                    .addContent(property.type().boxed())
                    .addContentLine(") value);");
        }
        checkPropertyValue.addContent("default -> throw new ")
                .addContent(VALIDATION_EXCEPTION)
                .addContent("(")
                .addContentLiteral("Invalid property name: ")
                .addContentLine(" + propertyName);")
                .addContentLine("};");

        classModel.addMethod(checkPropertyValue);
    }

    private void addCheckMethod(TypeName generatedType,
                                ClassModel.Builder classModel,
                                Collection<TypeName> constraintAnnotations,
                                FieldHandler fieldHandler,
                                String location,
                                TypedElementInfo element,
                                String propertyName) {

        classModel.addMethod(propertyCheck -> propertyCheck
                .name("check" + capitalize(propertyName))
                .accessModifier(AccessModifier.PRIVATE)
                .addParameter(context -> context.name("validation__ctx")
                        .type(VALIDATION_CONTEXT))
                .addParameter(value -> value.name("value")
                        .type(element.typeName()))
                .update(it -> processValidatedElement(generatedType,
                                                      constraintAnnotations,
                                                      fieldHandler,
                                                      it,
                                                      location,
                                                      element))
        );
    }

    private void processValidatedElement(TypeName generatedType,
                                         Collection<TypeName> constraintAnnotations,
                                         FieldHandler fieldHandler,
                                         Method.Builder checkMethod,
                                         String location,
                                         TypedElementInfo element) {

        checkMethod.addContent("try (var scope__" + location.toLowerCase(Locale.ROOT) + capitalize(element.elementName())
                                       + " = validation__ctx.scope(")
                .addContent(CONSTRAINT_VIOLATION_LOCATION)
                .addContent(".")
                .addContent(location)
                .addContent(", ")
                .addContentLiteral(element.elementName())
                .addContentLine(")) {");

        TypeName typeName = element.typeName();

        addValidationOfTypeArguments(generatedType,
                                     constraintAnnotations,
                                     checkMethod,
                                     fieldHandler,
                                     element,
                                     "value");

        if (element.hasAnnotation(VALIDATION_VALID)) {

            // add valid lookup
            String validatorField = addTypeValidator(fieldHandler, typeName);
            addValidationOfValid(checkMethod, validatorField, "value");
        }

        for (TypeName constraintAnnotation : constraintAnnotations) {
            element.findAnnotation(constraintAnnotation)
                    .ifPresent(annotation -> addValidationOfConstraint(generatedType,
                                                                       fieldHandler,
                                                                       checkMethod,
                                                                       annotation,
                                                                       location,
                                                                       element,
                                                                       "value"));
        }

        checkMethod.addContentLine("}");

    }

    private record Property(String name,
                            String checkMethodName,
                            TypeName type,
                            boolean method,
                            String getterName) {
        Property(String name, String checkMethodName, TypeName type) {
            this(name, checkMethodName, type, true, name);
        }

        Property(String name, String checkMethodName, TypeName type, boolean method) {
            this(name, checkMethodName, type, method, name);
        }

        Property(String name, String checkMethodName, TypeName type, String getterName) {
            this(name, checkMethodName, type, true, getterName);
        }
    }
}
