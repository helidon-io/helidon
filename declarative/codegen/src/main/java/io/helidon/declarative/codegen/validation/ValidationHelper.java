/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.TypeHierarchy;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATOR;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATOR_PROVIDER;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VIOLATION_LOCATION;
import static io.helidon.declarative.codegen.validation.ValidationTypes.TYPE_VALIDATOR;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALID;

final class ValidationHelper {
    private ValidationHelper() {
    }

    static String addTypeValidator(FieldHandler fieldHandler, TypeName validatedType) {
        TypeName validatorType = TypeName.builder(TYPE_VALIDATOR)
                .addTypeArgument(validatedType)
                .build();

        return fieldHandler.field(validatorType,
                                  "typeValidator",
                                  AccessModifier.PRIVATE,
                                  validatedType,
                                  it -> {
                                  },
                                  (ctr, name) -> {
                                      ctr.addParameter(param -> param.type(TypeName.builder(TypeNames.OPTIONAL)
                                                                                   .addTypeArgument(
                                                                                           validatorType)
                                                                                   .build())
                                                      .name(name + "_optional"))
                                              .addContent("this.")
                                              .addContent(name)
                                              .addContent(" = ")
                                              .addContent(name + "_optional")
                                              .addContent(".orElseThrow(() -> new ")
                                              .addContent(IllegalArgumentException.class)
                                              .addContent("(")
                                              .addContentLiteral("Type validator for ")
                                              .addContent(" + ")
                                              .addContent(validatedType.fqName())
                                              .addContent(".class.getName() + ")
                                              .addContentLiteral(
                                                      " is not available, maybe it is not annotated with Validation.Validated, "
                                                              + "or "
                                                              + "the annotation processor setup is missing.")
                                              .addContentLine("));");
                                  });
    }

    static void addValidationOfValid(ContentBuilder<?> contentBuilder, String validatorField, String varName) {
        contentBuilder.addContent(validatorField)
                .addContent(".check(validation__ctx, ")
                .addContent(varName)
                .addContentLine(");");
    }

    static ValidationContext validationContext(TypeName generatedType,
                                               Collection<TypeName> constraintAnnotations,
                                               ContentBuilder<?> contentBuilder,
                                               FieldHandler fieldHandler,
                                               TypedElementInfo element) {
        return new ValidationContext(generatedType, constraintAnnotations, contentBuilder, fieldHandler, element);
    }

    static void addValidationOfTypeArguments(ValidationContext context, String localVariableName) {
        addValidationOfContainerType(context,
                                     context.element().typeName(),
                                     localVariableName,
                                     0);
    }

    /**
     *
     * @param context       validation code generation context
     * @param constraint    the constraint - must be an annotation of the element or its type parameters
     * @param location      location of the check (i.e. parameter, return value etc.)
     * @param annotatedType type that carries the constraint annotation
     * @param varName       name of the variable to check using the constraint validator
     */
    static void addValidationOfConstraint(ValidationContext context,
                                          Annotation constraint,
                                          String location,
                                          TypeName annotatedType,
                                          String varName) {

        // create a constant with the fully qualified name of the constraint
        String constraintType = context.fieldHandler()
                .constant("CONSTRAINT",
                          TypeNames.STRING,
                          constraint.typeName(),
                          it -> it.addContentLiteral(constraint.typeName().fqName()));

        String validator = context.fieldHandler()
                .field(CONSTRAINT_VALIDATOR,
                       "constraintValidator",
                       AccessModifier.PRIVATE,
                       new ValidatorKey(ResolvedType.create(annotatedType), constraint),
                       it -> {
                       },
                       (ctr, name) -> {
                           ctr.addParameter(param -> param
                                           .name(name + "_provider")
                                           .addAnnotation(namedByConstant(context.generatedType(), constraintType))
                                           .type(CONSTRAINT_VALIDATOR_PROVIDER))
                                   .addContent("this.")
                                   .addContent(name)
                                   .addContent(" = ")
                                   .addContent(name + "_provider")
                                   .addContent(".create(")
                                   .addContentCreate(annotatedType)
                                   .addContent(", ")
                                   .addContentCreate(constraint)
                                   .addContentLine(");");
                       });

        context.contentBuilder()
                .addContent("validation__ctx.check(")
                .addContent(validator)
                .addContent(", ")
                .addContent(varName)
                .addContentLine(");");
    }

    static List<Annotation> findConstraintAnnotations(Collection<TypeName> constraintAnnotations, TypedElementInfo element) {
        // go through all annotations, and find if they are constraint; also go through all meta-annotations
        // the result must be in order of declaration (both on the element, and in meta-annotations)
        List<Annotation> result = new ArrayList<>();
        Set<Annotation> processed = new HashSet<>();

        findConstraintAnnotations(constraintAnnotations, element.annotations(), result, processed);

        return result;
    }

    private static List<Annotation> findConstraintAnnotations(Collection<TypeName> constraintAnnotations,
                                                              List<Annotation> annotations) {
        List<Annotation> result = new ArrayList<>();
        Set<Annotation> processed = new HashSet<>();

        findConstraintAnnotations(constraintAnnotations, annotations, result, processed);

        return result;
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, TypedElementInfo element) {
        return needsWork(constraintAnnotations, element, true);
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations,
                             TypedElementInfo element,
                             boolean includeParameters) {
        // the element itself is annotated either with valid or one of the constraints
        // a type parameter is annotated in the same way

        if (element.hasAnnotation(VALIDATION_VALID)) {
            return true;
        }
        for (TypeName constraintAnnotation : constraintAnnotations) {
            if (element.hasAnnotation(constraintAnnotation)) {
                return true;
            }
        }
        if (needsWork(constraintAnnotations, element.annotations())) {
            return true;
        }

        if (needsWork(constraintAnnotations, element.typeName())) {
            return true;
        }

        if (includeParameters) {
            for (TypedElementInfo param : element.parameterArguments()) {
                if (needsWork(constraintAnnotations, param)) {
                    return true;
                }
            }
        }

        return false;
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, TypeName typeName) {
        return needsWork(constraintAnnotations, TypeHierarchy.typeNameAnnotations(typeName));
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.typeName().equals(VALIDATION_VALID)) {
                return true;
            }
            for (TypeName constraintAnnotation : constraintAnnotations) {
                if (annotation.typeName().equals(constraintAnnotation)) {
                    return true;
                }
            }
            if (annotation.hasMetaAnnotation(VALIDATION_VALID)) {
                return true;
            }
            for (TypeName constraintAnnotation : constraintAnnotations) {
                if (annotation.hasMetaAnnotation(constraintAnnotation)) {
                    return true;
                }
            }
        }

        return false;
    }

    static Annotation namedByConstant(TypeName type, String constantName) {
        return Annotation.builder()
                .typeName(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED)
                .putProperty("value", AnnotationProperty.create("", type, constantName))
                .build();
    }

    static List<Annotation> findMetaAnnotations(List<Annotation> annotations, TypeName constraintAnnotation) {
        // we only care about meta-annotations, as direct annotations are already processed
        List<Annotation> response = new ArrayList<>();

        for (Annotation annotation : annotations) {
            var metaAnnotations = annotation.metaAnnotations();
            for (Annotation metaAnnotation : metaAnnotations) {
                if (metaAnnotation.typeName().equals(constraintAnnotation)) {
                    response.add(metaAnnotation);
                }
                response.addAll(findMetaAnnotations(metaAnnotation.metaAnnotations(), constraintAnnotation));
            }
        }

        return response;
    }

    static boolean metaAnnotated(TypedElementInfo element, TypeName checkValid) {
        for (Annotation annotation : element.annotations()) {
            if (annotation.hasMetaAnnotation(checkValid)) {
                return true;
            }
        }
        return false;
    }

    private static void findConstraintAnnotations(Collection<TypeName> constraintAnnotations,
                                                  List<Annotation> annotations,
                                                  List<Annotation> result,
                                                  Set<Annotation> processed) {

        for (Annotation annotation : annotations) {
            if (processed.add(annotation)) {
                // a constraint itself
                if (isConstraintAnnotation(constraintAnnotations, annotation)) {
                    result.add(annotation);
                }
                // maybe meta-annotated with a constraint
                findConstraintAnnotations(constraintAnnotations, annotation.metaAnnotations(), result, processed);
            }
        }
    }

    private static boolean isConstraintAnnotation(Collection<TypeName> constraintAnnotations, Annotation annotation) {
        if (constraintAnnotations.contains(annotation.typeName())) {
            return isConstraintAnnotation(annotation);
        }
        return hasDirectConstraintMetaAnnotation(annotation);
    }

    private static boolean isConstraintAnnotation(Annotation annotation) {
        return annotation.metaAnnotations().isEmpty()
                || hasDirectConstraintMetaAnnotation(annotation);
    }

    private static boolean hasDirectConstraintMetaAnnotation(Annotation annotation) {
        return annotation.metaAnnotations()
                .stream()
                .anyMatch(it -> it.typeName().equals(ValidationTypes.VALIDATION_CONSTRAINT));
    }

    private static void addValidationOfContainerType(ValidationContext context,
                                                     TypeName typeName,
                                                     String localVariableName,
                                                     int depth) {
        if (typeName.equals(TypeNames.SET)
                || typeName.equals(TypeNames.LIST)
                || typeName.equals(TypeNames.COLLECTION)
                || typeName.equals(TypeNames.OPTIONAL)) {
            addValidationOfSingleTypeArgument(context,
                                              typeName,
                                              localVariableName,
                                              depth);
        } else if (typeName.equals(TypeNames.MAP)) {
            addValidationOfMap(context,
                               typeName,
                               localVariableName,
                               depth);
        } else if (typeName.array()) {
            addValidationOfArrayComponent(context,
                                          typeName,
                                          localVariableName,
                                          depth);
        }
    }

    private static void addValidationOfArrayComponent(ValidationContext context,
                                                      TypeName typeName,
                                                      String localVariableName,
                                                      int depth) {
        Optional<TypeName> componentType = typeName.componentType();
        if (componentType.isEmpty() || !needsWork(context.constraintAnnotations(), componentType.get())) {
            return;
        }

        String elementVar = "validation__element" + depth;
        context.contentBuilder()
                .addContent("if (")
                .addContent(localVariableName)
                .addContentLine(" != null) {");
        context.contentBuilder()
                .addContent("for (var ")
                .addContent(elementVar)
                .addContent(" : ")
                .addContent(localVariableName)
                .addContentLine(") {");
        addValidationOfTypeName(context,
                                new TypeValidation(componentType.get(),
                                                   elementVar,
                                                   "ELEMENT",
                                                   "element",
                                                   depth + 1,
                                                   false,
                                                   false));
        context.contentBuilder().addContentLine("}");
        context.contentBuilder().addContentLine("}");
    }

    private static void addValidationOfMap(ValidationContext context,
                                           TypeName typeName,
                                           String localVariableName,
                                           int depth) {
        if (typeName.typeArguments().size() != 2) {
            return;
        }

        TypeName keyType = typeName.typeArguments().get(0);
        TypeName valueType = typeName.typeArguments().get(1);
        boolean keyNeedsWork = needsWork(context.constraintAnnotations(), keyType);
        boolean valueNeedsWork = needsWork(context.constraintAnnotations(), valueType);
        if (!keyNeedsWork && !valueNeedsWork) {
            return;
        }

        String entryVar = "validation__entry" + depth;
        String keyVar = "validation__key" + depth;
        String valueVar = "validation__value" + depth;

        context.contentBuilder()
                .addContent("if (")
                .addContent(localVariableName)
                .addContentLine(" != null) {");
        context.contentBuilder()
                .addContent("for (var ")
                .addContent(entryVar)
                .addContent(" : ")
                .addContent(localVariableName)
                .addContentLine(".entrySet()) {")
                .addContent("var ")
                .addContent(keyVar)
                .addContent(" = ")
                .addContent(entryVar)
                .addContentLine(".getKey();")
                .addContent("var ")
                .addContent(valueVar)
                .addContent(" = ")
                .addContent(entryVar)
                .addContentLine(".getValue();");

        if (keyNeedsWork) {
            addValidationOfTypeName(context,
                                    new TypeValidation(keyType,
                                                       keyVar,
                                                       "KEY",
                                                       "key",
                                                       depth + 1,
                                                       false,
                                                       true));
        }
        if (valueNeedsWork) {
            addValidationOfTypeName(context,
                                    new TypeValidation(valueType,
                                                       valueVar,
                                                       "ELEMENT",
                                                       "value",
                                                       depth + 1,
                                                       false,
                                                       true));
        }

        context.contentBuilder().addContentLine("}");
        context.contentBuilder().addContentLine("}");
    }

    private static void addValidationOfSingleTypeArgument(ValidationContext context,
                                                          TypeName typeName,
                                                          String localVariableName,
                                                          int depth) {
        if (typeName.typeArguments().isEmpty()) {
            return;
        }

        TypeName typeArgument = typeName.typeArguments().getFirst();
        if (!needsWork(context.constraintAnnotations(), typeArgument)) {
            return;
        }

        String elementVar = "validation__element" + depth;

        context.contentBuilder()
                .addContent("if (")
                .addContent(localVariableName)
                .addContentLine(" != null) {");
        if (typeName.equals(TypeNames.OPTIONAL)) {
            context.contentBuilder()
                    .addContent("if (")
                    .addContent(localVariableName)
                    .addContentLine(".isPresent()) {")
                    .addContent("var ")
                    .addContent(elementVar)
                    .addContent(" = ")
                    .addContent(localVariableName)
                    .addContentLine(".get();");
        } else {
            context.contentBuilder()
                    .addContent("for (var ")
                    .addContent(elementVar)
                    .addContent(" : ")
                    .addContent(localVariableName)
                    .addContentLine(") {");
        }

        addValidationOfTypeName(context,
                                new TypeValidation(typeArgument,
                                                   elementVar,
                                                   "ELEMENT",
                                                   "element",
                                                   depth + 1,
                                                   false,
                                                   false));

        context.contentBuilder().addContentLine("}");
        context.contentBuilder().addContentLine("}");
    }

    private static void addValidationOfTypeName(ValidationContext context, TypeValidation validation) {
        List<Annotation> annotations = directTypeAnnotations(validation.typeName());
        boolean hasValid = annotations.stream()
                .anyMatch(it -> it.typeName().equals(VALIDATION_VALID) || it.hasMetaAnnotation(VALIDATION_VALID));
        List<Annotation> constraints = findConstraintAnnotations(context.constraintAnnotations(), annotations);
        boolean currentNeedsWork = hasValid || !constraints.isEmpty();

        if (validation.forceScope() && needsWork(context.constraintAnnotations(), validation.typeName())) {
            scope(context.contentBuilder(), validation.location(), validation.locationName(), validation.depth());
            addCurrentTypeValidation(context, validation, hasValid, constraints);
            addNestedTypeValidation(context, validation);
            context.contentBuilder().addContentLine("}");
            return;
        }

        if (currentNeedsWork) {
            scope(context.contentBuilder(), validation.location(), validation.locationName(), validation.depth());
            addCurrentTypeValidation(context, validation, hasValid, constraints);
            context.contentBuilder().addContentLine("}");
        }

        addNestedTypeValidation(context, validation);
    }

    private static void scope(ContentBuilder<?> contentBuilder, String location, String locationName, int depth) {
        contentBuilder.addContent("try (var scope__")
                .addContent(location.toLowerCase(Locale.ROOT))
                .addContent(String.valueOf(depth))
                .addContent(" = validation__ctx.scope(")
                .addContent(CONSTRAINT_VIOLATION_LOCATION)
                .addContent(".")
                .addContent(location)
                .addContent(", ")
                .addContentLiteral(locationName)
                .addContentLine(")) {");
    }

    private static void addCurrentTypeValidation(ValidationContext context,
                                                 TypeValidation validation,
                                                 boolean hasValid,
                                                 List<Annotation> constraints) {
        if (hasValid) {
            String validatorField = addTypeValidator(context.fieldHandler(), validation.typeName());
            if (validation.guardValidType()) {
                String validVar = "validation__valid" + validation.depth();
                context.contentBuilder()
                        .addContent("if (")
                        .addContent(validation.localVariableName())
                        .addContent(" instanceof ")
                        .addContent(validation.typeName().genericTypeName())
                        .addContent(" ")
                        .addContent(validVar)
                        .addContentLine(") {");
                addValidationOfValid(context.contentBuilder(), validatorField, validVar);
                context.contentBuilder().addContentLine("}");
            } else {
                addValidationOfValid(context.contentBuilder(), validatorField, validation.localVariableName());
            }
        }

        for (Annotation constraint : constraints) {
            addValidationOfConstraint(context,
                                      constraint,
                                      validation.location(),
                                      validation.typeName(),
                                      validation.localVariableName());
        }
    }

    private static void addNestedTypeValidation(ValidationContext context, TypeValidation validation) {
        addValidationOfContainerType(context,
                                     validation.typeName(),
                                     validation.localVariableName(),
                                     validation.depth());
        for (TypeName lowerBound : validation.typeName().lowerBounds()) {
            addValidationOfTypeName(context,
                                    new TypeValidation(lowerBound,
                                                       validation.localVariableName(),
                                                       validation.location(),
                                                       validation.locationName(),
                                                       validation.depth() + 1,
                                                       true,
                                                       false));
        }
        for (TypeName upperBound : validation.typeName().upperBounds()) {
            addValidationOfTypeName(context,
                                    new TypeValidation(upperBound,
                                                       validation.localVariableName(),
                                                       validation.location(),
                                                       validation.locationName(),
                                                       validation.depth() + 1,
                                                       false,
                                                       false));
        }
    }

    private static List<Annotation> directTypeAnnotations(TypeName typeName) {
        if (typeName.inheritedAnnotations().isEmpty()) {
            return typeName.annotations();
        }
        List<Annotation> annotations = new ArrayList<>(typeName.annotations());
        annotations.addAll(typeName.inheritedAnnotations());
        return annotations;
    }

    private record ValidatorKey(ResolvedType annotatedType, Annotation constraint) {
    }

    record ValidationContext(TypeName generatedType,
                             Collection<TypeName> constraintAnnotations,
                             ContentBuilder<?> contentBuilder,
                             FieldHandler fieldHandler,
                             TypedElementInfo element) {
    }

    private record TypeValidation(TypeName typeName,
                                  String localVariableName,
                                  String location,
                                  String locationName,
                                  int depth,
                                  boolean guardValidType,
                                  boolean forceScope) {
    }
}
