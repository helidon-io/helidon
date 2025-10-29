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

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
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

    static void addValidationOfTypeArguments(TypeName generatedType,
                                             Collection<TypeName> constraintAnnotations,
                                             ContentBuilder<?> contentBuilder,
                                             FieldHandler fieldHandler,
                                             TypedElementInfo element,
                                             String localVariableName) {
        TypeName typeName = element.typeName();

        // check for types that we support that
        if (typeName.equals(TypeNames.SET)
                || typeName.equals(TypeNames.LIST)
                || typeName.equals(TypeNames.COLLECTION)
                || typeName.equals(TypeNames.OPTIONAL)) {
            addValidationForSingleTypeArgument(generatedType,
                                               constraintAnnotations,
                                               contentBuilder,
                                               fieldHandler,
                                               element,
                                               localVariableName,
                                               typeName);
        } else if (typeName.equals(TypeNames.MAP)) {
            addValidationOfMap(generatedType,
                               constraintAnnotations,
                               contentBuilder,
                               fieldHandler,
                               element,
                               localVariableName,
                               typeName);
        }
    }

    /**
     *
     * @param generatedType  type of the generated class
     * @param fieldHandler   field handler for the processed type
     * @param contentBuilder content where we should add the constraint validation code
     * @param constraint     the constraint - must be an annotation of the element or its type parameters
     * @param location       location of the check (i.e. parameter, return value etc.)
     * @param element        the element that is annotated, or contains this annotation
     * @param varName        name of the variable to check using the constraint validator
     */
    static void addValidationOfConstraint(TypeName generatedType,
                                          FieldHandler fieldHandler,
                                          ContentBuilder<?> contentBuilder,
                                          Annotation constraint,
                                          String location,
                                          TypedElementInfo element,
                                          String varName) {

        // create a constant with the fully qualified name of the constraint
        String constraintType = fieldHandler.constant("CONSTRAINT",
                                                      TypeNames.STRING,
                                                      constraint.typeName(),
                                                      it -> it.addContentLiteral(constraint.typeName().fqName()));

        String validator = fieldHandler.field(CONSTRAINT_VALIDATOR,
                                              "constraintValidator",
                                              AccessModifier.PRIVATE,
                                              constraint,
                                              it -> {
                                              },
                                              (ctr, name) -> {
                                                  ctr.addParameter(param -> param
                                                                  .name(name + "_provider")
                                                                  .addAnnotation(namedByConstant(generatedType,
                                                                                                 constraintType))
                                                                  .type(CONSTRAINT_VALIDATOR_PROVIDER))
                                                          .addContent("this.")
                                                          .addContent(name)
                                                          .addContent(" = ")
                                                          .addContent(name + "_provider")
                                                          .addContent(".create(")
                                                          .addContentCreate(element.typeName())
                                                          .addContent(", ")
                                                          .addContentCreate(constraint)
                                                          .addContentLine(");");
                                              });

        contentBuilder.addContent("validation__ctx.check(")
                .addContent(validator)
                .addContent(", ")
                .addContent(varName)
                .addContentLine(");");
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, TypedElementInfo element) {
        // the element itself is annotated either with valid or one of the constraints
        // a type parameter is annotated in the same way (only first level)

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

        // now type parameters of the element itself, or its parameters
        // (valid only for methods, but not present on fields so no problem to check)
        TypeName elementType = element.typeName();
        for (TypeName typeName : elementType.typeArguments()) {
            if (typeName.hasAnnotation(VALIDATION_VALID)) {
                return true;
            }
            for (TypeName constraintAnnotation : constraintAnnotations) {
                if (typeName.hasAnnotation(constraintAnnotation)) {
                    return true;
                }
            }
            if (needsWork(constraintAnnotations, typeName.annotations())) {
                return true;
            }
        }

        for (TypedElementInfo param : element.parameterArguments()) {
            if (needsWork(constraintAnnotations, param)) {
                return true;
            }
        }

        return false;
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
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

    private static void addValidationOfMap(TypeName generatedType,
                                           Collection<TypeName> constraintAnnotations,
                                           ContentBuilder<?> contentBuilder,
                                           FieldHandler fieldHandler,
                                           TypedElementInfo element,
                                           String localVariableName,
                                           TypeName typeName) {
        // handle map keys and values
        if (typeName.typeArguments().size() == 2) {
            TypeName keyType = typeName.typeArguments().get(0);
            TypeName valueType = typeName.typeArguments().get(1);

            boolean keyHasValid = keyType.hasAnnotation(VALIDATION_VALID);
            List<Annotation> keyConstraints = new ArrayList<>();
            boolean valueHasValid = valueType.hasAnnotation(VALIDATION_VALID);
            List<Annotation> valueConstraints = new ArrayList<>();

            // now for each constraint annotation...
            for (TypeName constraintAnnotation : constraintAnnotations) {
                keyType.findAnnotation(constraintAnnotation).ifPresent(keyConstraints::add);
            }
            for (TypeName constraintAnnotation : constraintAnnotations) {
                valueType.findAnnotation(constraintAnnotation).ifPresent(valueConstraints::add);
            }

            if (keyHasValid || valueHasValid || !keyConstraints.isEmpty() || !valueConstraints.isEmpty()) {
                // need to go through the collection/optional
                contentBuilder.addContent("if (")
                        .addContent(localVariableName)
                        .addContentLine(" != null) {");

                contentBuilder.addContent("for (var validation__entry : ")
                        .addContent(localVariableName)
                        .addContentLine(".entrySet()) {")
                        .addContentLine("var validation__key = validation__entry.getKey();")
                        .addContentLine("var validation__value = validation__entry.getValue();");

                if (keyHasValid || !keyConstraints.isEmpty()) {
                    contentBuilder.addContent("try (var scope__mapkey = validation__ctx.scope(")
                            .addContent(CONSTRAINT_VIOLATION_LOCATION)
                            .addContent(".KEY, ")
                            .addContentLiteral("key")
                            .addContentLine(")) {");
                }
                if (keyHasValid) {
                    String validatorField = addTypeValidator(fieldHandler, keyType);
                    addValidationOfValid(contentBuilder, validatorField, "validation__key");
                }
                for (Annotation constraint : keyConstraints) {
                    addValidationOfConstraint(generatedType,
                                              fieldHandler,
                                              contentBuilder,
                                              constraint,
                                              "KEY",
                                              element,
                                              "validation__key");
                }

                if (keyHasValid || !keyConstraints.isEmpty()) {
                    contentBuilder.addContentLine("}");
                }

                if (valueHasValid || !valueConstraints.isEmpty()) {
                    contentBuilder.addContent("try (var scope__mapelement = validation__ctx.scope(")
                            .addContent(CONSTRAINT_VIOLATION_LOCATION)
                            .addContent(".ELEMENT, ")
                            .addContentLiteral("value")
                            .addContentLine(")) {");
                }
                if (valueHasValid) {
                    String validatorField = addTypeValidator(fieldHandler, valueType);
                    addValidationOfValid(contentBuilder, validatorField, "validation__value");
                }

                for (Annotation constraint : valueConstraints) {
                    addValidationOfConstraint(generatedType,
                                              fieldHandler,
                                              contentBuilder,
                                              constraint,
                                              "ELEMENT",
                                              element,
                                              "validation__value");
                }

                if (valueHasValid || !valueConstraints.isEmpty()) {
                    contentBuilder.addContentLine("}");
                }

                contentBuilder.addContentLine("}");
                contentBuilder.addContentLine("}");
            }
        }
    }

    private static void addValidationForSingleTypeArgument(TypeName generatedType,
                                                           Collection<TypeName> constraintAnnotations,
                                                           ContentBuilder<?> contentBuilder,
                                                           FieldHandler fieldHandler,
                                                           TypedElementInfo element,
                                                           String localVariableName,
                                                           TypeName typeName) {
        // handle collection type argument annotations
        if (!typeName.typeArguments().isEmpty()) {
            boolean isOptional = typeName.equals(TypeNames.OPTIONAL);
            TypeName maybeAnnotated = typeName.typeArguments().getFirst();
            boolean hasValid = maybeAnnotated.hasAnnotation(VALIDATION_VALID)
                    || !findMetaAnnotations(maybeAnnotated.annotations(), VALIDATION_VALID).isEmpty();
            List<Annotation> constraints = new ArrayList<>();

            // now for each constraint annotation...
            for (TypeName constraintAnnotation : constraintAnnotations) {
                maybeAnnotated.findAnnotation(constraintAnnotation).ifPresent(constraints::add);
                constraints.addAll(findMetaAnnotations(maybeAnnotated.annotations(), constraintAnnotation));
            }

            if (hasValid || !constraints.isEmpty()) {
                // need to go through the collection/optional
                contentBuilder.addContent("if (")
                        .addContent(localVariableName)
                        .addContentLine(" != null) {");
                if (isOptional) {
                    contentBuilder.addContent("if (")
                            .addContent(localVariableName)
                            .addContentLine(".isPresent()) {")
                            .addContent("var validation__element = ")
                            .addContent(localVariableName)
                            .addContentLine(".get();");
                } else {
                    contentBuilder.addContent("for (var validation__element : ")
                            .addContent(localVariableName)
                            .addContentLine(") {");
                }
                contentBuilder.addContent("try (var scope__element = validation__ctx.scope(")
                        .addContent(CONSTRAINT_VIOLATION_LOCATION)
                        .addContent(".")
                        .addContent("ELEMENT")
                        .addContentLine(", \"element\")) {");

                if (hasValid) {
                    String validatorField = addTypeValidator(fieldHandler, maybeAnnotated);
                    addValidationOfValid(contentBuilder, validatorField, "validation__element");
                }

                for (Annotation constraint : constraints) {
                    addValidationOfConstraint(generatedType,
                                              fieldHandler,
                                              contentBuilder,
                                              constraint,
                                              "ELEMENT",
                                              element,
                                              "validation__element");
                }

                contentBuilder.addContentLine("}");
                contentBuilder.addContentLine("}");
                contentBuilder.addContentLine("}");
            }
        }
    }
}
