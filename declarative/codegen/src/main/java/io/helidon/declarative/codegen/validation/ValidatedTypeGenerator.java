package io.helidon.declarative.codegen.validation;

import java.util.Collection;
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
import io.helidon.common.types.TypedElementInfo;
import io.helidon.declarative.codegen.DeclarativeTypes;
import io.helidon.service.codegen.FieldHandler;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.ServiceCodegenTypes;

import static io.helidon.declarative.codegen.validation.ValidationHelper.addTypeValidator;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfConstraint;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfTypeArguments;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfValid;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATION_CONTEXT;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_TYPE_VALIDATOR;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALID;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALIDATED;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATOR_RESPONSE;

class ValidatedTypeGenerator {
    private static final TypeName GENERATOR = TypeName.create(ValidatedTypeGenerator.class);
    private final RegistryRoundContext roundContext;
    private final Collection<TypeName> constraintAnnotations;

    ValidatedTypeGenerator(RegistryRoundContext roundContext, Collection<TypeName> constraintAnnotations) {
        this.roundContext = roundContext;
        this.constraintAnnotations = constraintAnnotations;
    }

    public void process(TypeInfo typeInfo) {
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
                                      .from(VALIDATION_TYPE_VALIDATOR)
                                      .addTypeArgument(triggerType)
                                      .build());

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        FieldHandler fieldHandler = FieldHandler.create(classModel, constructor);
        Method.Builder checkMethod = Method.builder()
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(VALIDATOR_RESPONSE)
                .name("check")
                .addParameter(context -> context.name("validation__ctx")
                        .type(CONSTRAINT_VALIDATION_CONTEXT))
                .addParameter(instance -> instance.name("validation__instance")
                        .type(triggerType))
                .addContentLine("var validation__res = validation__ctx.response();")
                .addContentLine("");

        // now we can add all the fields necessary to validate this type
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

        classModel.addConstructor(constructor);
        classModel.addMethod(checkMethod.addContentLine("return validation__res;"));
        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void processValidatedRecord(TypeName generatedType,
                                        Collection<TypeName> constraintAnnotations, TypeInfo type, ClassModel.Builder classModel,
                                        FieldHandler fieldHandler,
                                        Method.Builder checkMethod) {
        type.elementInfo()
                .stream()
                .filter(it -> it.kind() == ElementKind.RECORD_COMPONENT)
                .forEach(element -> {
                    checkMethod.addContent("var ")
                            .addContent(element.elementName())
                            .addContent(" = validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine("();");
                    processValidatedElement(generatedType,
                                            constraintAnnotations,
                                            fieldHandler,
                                            checkMethod,
                                            "RECORD_COMPONENT",
                                            element);
                    checkMethod.addContentLine("");
                });
    }

    private void processValidatedClass(TypeName generatedType,
                                       Collection<TypeName> constraintAnnotations,
                                       TypeInfo type,
                                       ClassModel.Builder classModel,
                                       FieldHandler fieldHandler,
                                       Method.Builder checkMethod) {

        // non-private non-static methods that match getter pattern (we only add those annotated with a constraint or Valid)
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(Predicate.not(ElementInfoPredicates::isVoid))
                .forEach(element -> {
                    checkMethod.addContent("var ")
                            .addContent(element.elementName())
                            .addContent(" = validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine("();");
                    processValidatedElement(generatedType,
                                            constraintAnnotations,
                                            fieldHandler,
                                            checkMethod,
                                            "RETURN_VALUE",
                                            element);
                    checkMethod.addContentLine("");
                });

        // non-private non-static fields
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .forEach(element -> {
                    checkMethod.addContent("var ")
                            .addContent(element.elementName())
                            .addContent(" = validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine(";");
                    processValidatedElement(generatedType,
                                            constraintAnnotations,
                                            fieldHandler,
                                            checkMethod,
                                            "FIELD",
                                            element);
                    checkMethod.addContentLine("");
                });
    }

    private void processValidatedInterface(TypeName generatedType,
                                           Collection<TypeName> constraintAnnotations,
                                           TypeInfo type,
                                           ClassModel.Builder classModel,
                                           FieldHandler fieldHandler, Method.Builder checkMethod) {
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(Predicate.not(ElementInfoPredicates::isVoid))
                .forEach(element -> {
                    checkMethod.addContent("var ")
                            .addContent(element.elementName())
                            .addContent(" = validation__instance.")
                            .addContent(element.elementName())
                            .addContentLine("();");
                    processValidatedElement(generatedType,
                                            constraintAnnotations,
                                            fieldHandler,
                                            checkMethod,
                                            "RETURN_VALUE",
                                            element);
                    checkMethod.addContentLine("");
                });
    }

    private void processValidatedElement(TypeName generatedType,
                                         Collection<TypeName> constraintAnnotations,
                                         FieldHandler fieldHandler,
                                         Method.Builder checkMethod,
                                         String location,
                                         TypedElementInfo element) {

        TypeName typeName = element.typeName();

        addValidationOfTypeArguments(generatedType,
                                     constraintAnnotations,
                                     checkMethod,
                                     fieldHandler,
                                     element,
                                     location,
                                     element.elementName());

        if (element.hasAnnotation(VALIDATION_VALID)) {

            // add valid lookup
            String validatorField = addTypeValidator(fieldHandler, typeName);
            addValidationOfValid(checkMethod, validatorField, location, element.elementName());
        }

        for (TypeName constraintAnnotation : constraintAnnotations) {
            element.findAnnotation(constraintAnnotation)
                    .ifPresent(annotation -> addValidationOfConstraint(generatedType,
                                                                       fieldHandler,
                                                                       checkMethod,
                                                                       annotation,
                                                                       location,
                                                                       element,
                                                                       element.elementName()));
        }
    }
}
