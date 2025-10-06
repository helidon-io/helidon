package io.helidon.declarative.codegen.validation;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Constructor;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
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

import static io.helidon.declarative.codegen.validation.ValidationHelper.addTypeValidator;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfConstraint;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfTypeArguments;
import static io.helidon.declarative.codegen.validation.ValidationHelper.addValidationOfValid;
import static io.helidon.declarative.codegen.validation.ValidationTypes.CONSTRAINT_VALIDATION_CONTEXT;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALID;

class InterceptorGenerator {
    private static final TypeName GENERATOR = TypeName.create(InterceptorGenerator.class);

    private final RegistryRoundContext roundContext;
    private final Collection<TypeName> constraintAnnotations;

    InterceptorGenerator(RegistryRoundContext roundContext, Collection<TypeName> constraintAnnotations) {
        this.roundContext = roundContext;
        this.constraintAnnotations = constraintAnnotations;
    }

    public void process(TypeInfo type) {
        // a type that has methods with constraints
        // if the type is not a service, fail, unless annotated with @Validated
        // so what can be intercepted:
        // injected fields
        // constructor that is injected
        // methods (non-private)

        // we will make this bold assumption the user knows what they are doing, and not validate the
        // fact this is a service - the reason is that the generated interceptor will just be ignored if it is not
        AtomicInteger interceptorCounter = new AtomicInteger();

        // first find field injection points
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                .forEach(element -> {
                    generateInterceptor(type, interceptorCounter, element, "FIELD");
                });

        // then find constructors
        var constructors = type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isConstructor)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .collect(Collectors.toUnmodifiableList());
        TypedElementInfo constructor;
        if (constructors.size() == 1) {
            constructor = constructors.getFirst();
        } else if (constructors.isEmpty()) {
            constructor = null;
        } else {
            constructor = constructors.stream()
                    .filter(ElementInfoPredicates.hasAnnotation(ServiceCodegenTypes.SERVICE_ANNOTATION_INJECT))
                    .findFirst()
                    .orElse(null);
        }
        if (constructor != null) {
            generateInterceptor(type, interceptorCounter, constructor, "CONSTRUCTOR");
        }

        // and finally annotated methods
        type.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                .filter(Predicate.not(ElementInfoPredicates::isStatic))
                .forEach(element -> {
                    generateInterceptor(type, interceptorCounter, element, "METHOD");
                });
    }

    private void generateInterceptor(TypeInfo interceptedType,
                                     AtomicInteger interceptorCounter,
                                     TypedElementInfo element,
                                     String location) {
        if (!needsWork(element)) {
            return;
        }

        TypeName typeName = interceptedType.typeName();
        TypeName generatedType = TypeName.builder()
                .packageName(typeName.packageName())
                .className(typeName.classNameWithEnclosingNames()
                                   .replace('.', '_') + "__ValidationInterceptor_" + interceptorCounter.getAndIncrement())
                .build();

        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .copyright(CodegenUtil.copyright(GENERATOR, typeName, generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR, typeName, generatedType, "0", ""))
                .addAnnotation(DeclarativeTypes.SINGLETON_ANNOTATION)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(Annotation.create(ServiceCodegenTypes.SERVICE_ANNOTATION_NAMED,
                                                 typeName.fqName() + "." + element.signature().text()))
                .addInterface(ServiceCodegenTypes.INTERCEPTION_ELEMENT_INTERCEPTOR);

        Constructor.Builder constructor = Constructor.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE);

        FieldHandler fieldHandler = FieldHandler.create(classModel, constructor);

        TypeName genericVType = TypeName.builder()
                .className("V")
                .generic(true)
                .build();

        Method.Builder proceedMethod = Method.builder()
                .addGenericArgument(TypeArgument.builder()
                                            .token("V")
                                            .build())
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(genericVType)
                .name("proceed")
                .addParameter(ctx -> ctx.name("interception__ctx")
                        .type(ServiceCodegenTypes.INTERCEPTION_CONTEXT))
                .addParameter(chain -> chain.name("interception__chain")
                        .type(TypeName.builder(ServiceCodegenTypes.INTERCEPTION_CHAIN)
                                      .addTypeArgument(genericVType)
                                      .build()))
                .addParameter(args -> args.name("interception__args")
                        .type(TypeName.builder(TypeNames.OBJECT)
                                      .vararg(true)
                                      .build()))
                .addThrows(thrown -> thrown.type(Exception.class))
                .addContent("var validation__ctx = ")
                .addContent(CONSTRAINT_VALIDATION_CONTEXT)
                .addContent(".create(")
                .addContent(typeName)
                .addContentLine(".class, interception__ctx.serviceInstance().orElse(null));")
                .addContentLine("var validation__res = validation__ctx.response();")
                .addContentLine("");

        if (element.kind() == ElementKind.FIELD) {
            String name = element.elementName();
            proceedMethod.addContent("var ")
                    .addContent(name)
                    .addContentLine(" = interception__args[0];");
            addValidators(generatedType, proceedMethod, fieldHandler, element, location, name);
        } else {
            // constructor or method
            var params = element.parameterArguments();
            for (int i = 0; i < params.size(); i++) {
                var param = params.get(i);
                if (needsWork(param)) {
                    String name = element.elementName();
                    proceedMethod.addContent("var ")
                            .addContent(name)
                            .addContent(" = (")
                            .addContent(param.typeName())
                            .addContent(") interception__args[")
                            .addContent(String.valueOf(i))
                            .addContentLine("];");
                    addValidators(generatedType, proceedMethod, fieldHandler, param, "PARAMETER", name);
                }
            }
        }

        proceedMethod.addContentLine("")
                .addContentLine("if (validation__res.failed()) {")
                .addContentLine("throw validation__res.toException();")
                .addContentLine("}")
                .addContentLine("")
                .addContentLine("var interception__res = interception__chain.proceed(interception__args);")
                .addContentLine("");

        if (element.kind() == ElementKind.METHOD) {
            addValidators(generatedType,
                          proceedMethod,
                          fieldHandler,
                          element,
                          "METHOD",
                          "interception__res");
        }

        classModel.addConstructor(constructor);
        classModel.addMethod(proceedMethod.addContentLine("")
                                     .addContentLine("if (validation__res.failed()) {")
                                     .addContentLine("throw validation__res.toException();")
                                     .addContentLine("}")
                                     .addContentLine("")
                                     .addContentLine("return interception__res;"));
        roundContext.addGeneratedType(generatedType, classModel, GENERATOR);
    }

    private void addValidators(TypeName generatedType,
                               Method.Builder proceedMethod,
                               FieldHandler fieldHandler,
                               TypedElementInfo element,
                               String location,
                               String localVariableName) {
        // start with annotations on the element itself
        if (element.hasAnnotation(VALIDATION_VALID)) {
            String fieldName = addTypeValidator(fieldHandler, element.typeName());
            addValidationOfValid(proceedMethod, fieldName, location, localVariableName);
        }

        for (TypeName constraintAnnotation : constraintAnnotations) {
            element.findAnnotation(constraintAnnotation)
                    .ifPresent(it -> addValidationOfConstraint(generatedType,
                                                               fieldHandler,
                                                               proceedMethod,
                                                               it,
                                                               location,
                                                               element,
                                                               localVariableName));
        }

        addValidationOfTypeArguments(generatedType,
                                     constraintAnnotations,
                                     proceedMethod,
                                     fieldHandler,
                                     element,
                                     location,
                                     localVariableName);
    }

    private boolean needsWork(TypedElementInfo element) {
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
        }

        for (TypedElementInfo param : element.parameterArguments()) {
            if (needsWork(param)) {
                return true;
            }
        }

        return false;
    }
}
