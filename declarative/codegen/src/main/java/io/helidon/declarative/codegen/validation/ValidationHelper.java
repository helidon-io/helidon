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

import static io.helidon.declarative.codegen.validation.ValidationTypes.CHECK_VALID;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATOR;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATOR_PROVIDER;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VIOLATION_LOCATION;
import static io.helidon.declarative.codegen.validation.ValidationTypes.TYPE_VALIDATOR;

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

    static void addValidationOfValid(ContentBuilder<?> contentBuilder, String validatorField, String location, String varName) {
        checkAndMerge(contentBuilder, location, validatorField, varName);
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

        checkAndMerge(contentBuilder, location, validator, varName);
    }

    static boolean needsWork(Collection<TypeName> constraintAnnotations, TypedElementInfo element) {
        // the element itself is annotated either with valid or one of the constraints
        // a type parameter is annotated in the same way (only first level)

        if (element.hasAnnotation(CHECK_VALID)) {
            return true;
        }
        for (TypeName constraintAnnotation : constraintAnnotations) {
            if (element.hasAnnotation(constraintAnnotation)) {
                return true;
            }
        }

        // now type parameters of the element itself, or its parameters
        // (valid only for methods, but not present on fields so no problem to check)
        TypeName elementType = element.typeName();
        for (TypeName typeName : elementType.typeArguments()) {
            if (typeName.hasAnnotation(CHECK_VALID)) {
                return true;
            }
            for (TypeName constraintAnnotation : constraintAnnotations) {
                if (typeName.hasAnnotation(constraintAnnotation)) {
                    return true;
                }
            }
        }

        for (TypedElementInfo param : element.parameterArguments()) {
            if (needsWork(constraintAnnotations, param)) {
                return true;
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

            boolean keyHasValid = keyType.hasAnnotation(CHECK_VALID);
            List<Annotation> keyConstraints = new ArrayList<>();
            boolean valueHasValid = valueType.hasAnnotation(CHECK_VALID);
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
                    contentBuilder.addContent("validation__ctx.enter(")
                            .addContent(CONSTRAINT_VIOLATION_LOCATION)
                            .addContent(".KEY, ")
                            .addContentLiteral("key")
                            .addContentLine(");");
                }
                if (keyHasValid) {
                    String validatorField = addTypeValidator(fieldHandler, keyType);
                    addValidationOfValid(contentBuilder, validatorField, "KEY", "validation__key");
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
                    contentBuilder.addContentLine("// leave map key");
                    contentBuilder.addContentLine("validation__ctx.leave();");
                }

                if (valueHasValid || !valueConstraints.isEmpty()) {
                    contentBuilder.addContent("validation__ctx.enter(")
                            .addContent(CONSTRAINT_VIOLATION_LOCATION)
                            .addContent(".ELEMENT, ")
                            .addContentLiteral("value")
                            .addContentLine(");");
                }
                if (valueHasValid) {
                    String validatorField = addTypeValidator(fieldHandler, valueType);
                    addValidationOfValid(contentBuilder, validatorField, "ELEMENT", "validation__value");
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
                    contentBuilder.addContentLine("// leave map value");
                    contentBuilder.addContentLine("validation__ctx.leave();");
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
            boolean hasValid = maybeAnnotated.hasAnnotation(CHECK_VALID);
            List<Annotation> constraints = new ArrayList<>();

            // now for each constraint annotation...
            for (TypeName constraintAnnotation : constraintAnnotations) {
                maybeAnnotated.findAnnotation(constraintAnnotation).ifPresent(constraints::add);
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
                contentBuilder.addContent("validation__ctx.enter(")
                        .addContent(CONSTRAINT_VIOLATION_LOCATION)
                        .addContent(".")
                        .addContent("ELEMENT")
                        .addContentLine(", \"element\");");

                if (hasValid) {
                    String validatorField = addTypeValidator(fieldHandler, maybeAnnotated);
                    addValidationOfValid(contentBuilder, validatorField, "ELEMENT", "validation__element");
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

                contentBuilder.addContentLine("// leave element");
                contentBuilder.addContentLine("validation__ctx.leave();");

                contentBuilder.addContentLine("}");
                contentBuilder.addContentLine("}");
            }
        }
    }

    private static void checkAndMerge(ContentBuilder<?> contentBuilder,
                                      String location,
                                      String validatorVar,
                                      String validatedVar) {
        contentBuilder.addContent("validation__res = validation__res.merge(")
                .addContent(validatorVar)
                .addContent(".check(validation__ctx, ")
                .addContent(validatedVar)
                .addContentLine("));");
    }
}
