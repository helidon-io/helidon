package io.helidon.faulttolerance.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.codegen.InjectionCodegenContext;
import io.helidon.inject.codegen.RoundContext;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;

import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_NAMED;
import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_SINGLETON;

class FallbackExtension implements InjectCodegenExtension {
    private static final Annotation SINGLETON_ANNOTATION = Annotation.create(INJECTION_SINGLETON);
    private static final TypeName ERROR_CHECKER = TypeName.create("io.helidon.faulttolerance.ErrorChecker");
    private static final TypeName SET_OF_THROWABLES = TypeName.builder(TypeName.create(Set.class))
            .addTypeArgument(TypeName.builder(TypeName.create(Class.class))
                                     .addTypeArgument(TypeName.builder(TypeName.create(Throwable.class))
                                                              .wildcard(true)
                                                              .build())
                                     .build())
            .build();
    private static final TypeName LIST_OF_GENERIC_TYPES = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeName.builder(TypeNames.GENERIC_TYPE)
                                     .addTypeArgument(TypeName.create("?"))
                                     .build())
            .build();

    private final InjectionCodegenContext ctx;

    FallbackExtension(InjectionCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypedElementInfo> elements =
                roundContext.annotatedElements(FallbackExtensionProvider.FALLBACK_ANNOTATION);

        Map<TypeName, TypeInfo> types = new HashMap<>();
        for (TypeInfo info : roundContext.types()) {
            types.put(info.typeName(), info);
        }
        for (TypedElementInfo element : elements) {
            process(types, element);
        }
    }

    private void process(Map<TypeName, TypeInfo> types, TypedElementInfo element) {
        if (element.enclosingType().isEmpty()) {
            throw new CodegenException(
                    "Fallback annotation is only allowed on a method, yet this element does not have an enclosing type: "
                            + element);
        }
        TypeName enclosingType = element.enclosingType().get();
        TypeInfo typeInfo = types.get(enclosingType);
        if (typeInfo == null) {
            throw new CodegenException(
                    "Fallback annotation is expected on a type processed as part of this annotation round, yet the type info is"
                            + " not available for " + element);
        }

        Annotation fallbackAnnotation = element.annotation(FallbackExtensionProvider.FALLBACK_ANNOTATION);
        String methodName = element.elementName();
        TypeName generatedType = generatedType(enclosingType, methodName);

        ClassModel.Builder classModel = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addAnnotation(SINGLETON_ANNOTATION)
                .addAnnotation(Annotation.create(INJECTION_NAMED, enclosingType.fqName() + "." + methodName))
                .type(generatedType)
                .addInterface(fallbackMethodType(element.typeName(), enclosingType))
                .sortStaticFields(false);

        fallbackMethod(classModel, typeInfo, element, fallbackAnnotation);
        parameterTypesMethod(classModel, element.parameterArguments());

        ctx.addType(generatedType, classModel, enclosingType, typeInfo.originatingElement().orElse(enclosingType));
    }

    private void parameterTypesMethod(ClassModel.Builder classModel, List<TypedElementInfo> arguments) {
        classModel.addField(paramTypes -> paramTypes
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(LIST_OF_GENERIC_TYPES)
                .name("PARAM_TYPES")
                .update(it -> listOfGenericTypes(it, arguments))
        );

        classModel.addMethod(paramTypes -> paramTypes
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(LIST_OF_GENERIC_TYPES)
                .name("parameterTypes")
                .addContentLine("return PARAM_TYPES;")
        );
    }

    private void listOfGenericTypes(Field.Builder field, List<TypedElementInfo> arguments) {
        field.addContent(List.class)
                .addContent(".of");
        if (arguments.isEmpty()) {
            field.addContent("()");
            return;
        }
        Iterator<TypedElementInfo> iterator = arguments.iterator();

        field.addContentLine("(")
                .increaseContentPadding()
                .increaseContentPadding();

        while (iterator.hasNext()) {
            TypedElementInfo next = iterator.next();
            TypeName typeName = next.typeName();
            field.addContent("new ")
                    .addContent(TypeNames.GENERIC_TYPE)
                    .addContent("<")
                    .addContent(typeName)
                    .addContent("> () {}");
            if (iterator.hasNext()) {
                field.addContent(",");
            }
            field.addContentLine("");
        }

        field.decreaseContentPadding()
                .decreaseContentPadding()
                .addContent(")");
    }

    private void fallbackMethod(ClassModel.Builder classModel,
                                TypeInfo typeInfo,
                                TypedElementInfo element,
                                Annotation fallbackAnnotation) {

        errorCheckFields(classModel, fallbackAnnotation);

        String fallbackMethodName = fallbackAnnotation.value()
                .orElseThrow(() -> new CodegenException("value is mandatory on Fallback annotation. Missing for: " + element));
        TypeName returnType = element.typeName().boxed();
        boolean isVoid = TypeNames.BOXED_VOID.equals(returnType);

        // then the implementation of interface method to fallback
        List<TypeName> expectedArguments = element.parameterArguments()
                .stream()
                .map(TypedElementInfo::typeName)
                .map(TypeName::boxed)
                .toList();
        List<TypeName> argsWithThrowable = new ArrayList<>(expectedArguments);
        argsWithThrowable.add(TypeName.create(Throwable.class));

        List<TypedElementInfo> matchingByName = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates.elementName(fallbackMethodName))
                .filter(ElementInfoPredicates::isMethod)
                .toList();
        if (matchingByName.isEmpty()) {
            throw new CodegenException("Could not find matching fallback method for name "
                                               + fallbackMethodName + " in "
                                               + typeInfo.typeName().fqName() + ".",
                                       typeInfo.originatingElement().orElseGet(typeInfo::typeName));
        }

        // there is at least one method correctly named, let's find the right one
        boolean expectsThrowable = false;
        boolean found = false;
        TypedElementInfo fallbackMethod = null;
        List<BadCandidate> badCandidates = new ArrayList<>();

        Predicate<TypedElementInfo> withThrowable = ElementInfoPredicates.hasParams(argsWithThrowable);
        Predicate<TypedElementInfo> noThrowable = ElementInfoPredicates.hasParams(expectedArguments);
        for (TypedElementInfo elementInfo : matchingByName) {
            String reason;
            if (elementInfo.typeName().resolvedName().equals(returnType.resolvedName())) {
                if (withThrowable.test(elementInfo)) {
                    expectsThrowable = true;
                    found = true;
                    fallbackMethod = elementInfo;
                    break;
                }
                if (noThrowable.test(elementInfo)) {
                    found = true;
                    fallbackMethod = elementInfo;
                    break;
                }
                reason = "Method did not match arguments of annotated method";
            } else {
                reason = "Method return type did not match return type of annotated method";
            }

            badCandidates.add(new BadCandidate(elementInfo, reason));
        }
        if (!found) {
            throw new CodegenException("Could not find matching fallback method for name " + fallbackMethodName + ","
                                               + " following bad candidates found: " + badCandidates);
        }
        boolean isStatic = fallbackMethod.elementModifiers().contains(Modifier.STATIC);

        boolean finalExpectsThrowable = expectsThrowable;
        // and the invocation of the fallback
        classModel.addMethod(fallback -> fallback
                .addAnnotation(Annotations.OVERRIDE)
                .accessModifier(AccessModifier.PUBLIC)
                .returnType(returnType)
                .name("fallback")
                .addParameter(serviceParam -> serviceParam
                        .type(typeInfo.typeName())
                        .name("service")
                )
                .addParameter(throwableParam -> throwableParam
                        .type(Throwable.class)
                        .name("throwable"))
                .addParameter(paramsParam -> paramsParam
                        .type(Object.class) // should be vararg
                        .optional(true)
                        .name("params"))
                .addThrows(it -> it.type(Throwable.class))
                .addContentLine("if (ERROR_CHECKER.shouldSkip(throwable)) {")
                .addContentLine("throw throwable;")
                .decreaseContentPadding()
                .addContentLine("} else {")
                .update(it -> fallbackMethodBody(typeInfo,
                                                 element,
                                                 it,
                                                 fallbackMethodName,
                                                 isVoid,
                                                 isStatic,
                                                 finalExpectsThrowable))
                .addContentLine("}")
        );
    }

    private void fallbackMethodBody(TypeInfo typeInfo,
                                    TypedElementInfo element,
                                    Method.Builder method,
                                    String fallbackMethodName,
                                    boolean isVoid,
                                    boolean isStatic,
                                    boolean expectsThrowable) {
        if (!isVoid) {
            method.addContent("return ");
        }
        if (isStatic) {
            method.addContent(typeInfo.typeName());
        } else {
            method.addContent("service");
        }
        method.addContent(".")
                .addContent(fallbackMethodName)
                .addContent("(");

        // now all parameters (and maybe throwable)
        Iterator<TypedElementInfo> paramsIter = element.parameterArguments().iterator();
        int index = 0;
        while (paramsIter.hasNext()) {
            TypedElementInfo next = paramsIter.next();
            method.addContent("(")
                    .addContent(next.typeName())
                    .addContent(") params[")
                    .addContent(String.valueOf(index))
                    .addContent("]");
            if (paramsIter.hasNext()) {
                method.addContent(", ");
            }
            index++;
        }
        if (expectsThrowable) {
            if (!element.parameterArguments().isEmpty()) {
                method.addContent(", ");
            }
            method.addContent("throwable");
        }

        method.addContentLine(");");

        if (isVoid) {
            method.addContentLine("return null");
        }
    }

    private void errorCheckFields(ClassModel.Builder classModel, Annotation fallbackAnnotation) {
        // we need set of throwables to apply on, skip on, and error check fields
        List<TypeName> applyOn = fallbackAnnotation.typeValues("applyOn")
                .orElseGet(List::of);
        List<TypeName> skipOn = fallbackAnnotation.typeValues("skipOn")
                .orElseGet(List::of);

        classModel.addField(applyOnField -> applyOnField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(SET_OF_THROWABLES)
                .name("APPLY_ON")
                .update(it -> throwableSet(it, applyOn))
        );

        classModel.addField(skipOnField -> skipOnField
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(SET_OF_THROWABLES)
                .name("SKIP_ON")
                .update(it -> throwableSet(it, skipOn))
        );

        classModel.addField(errorChecker -> errorChecker
                .accessModifier(AccessModifier.PRIVATE)
                .isFinal(true)
                .isStatic(true)
                .type(ERROR_CHECKER)
                .name("ERROR_CHECKER")
                .addContent(ERROR_CHECKER)
                .addContent(".create(SKIP_ON, APPLY_ON)")
        );
    }

    private void throwableSet(Field.Builder field, List<TypeName> listOfThrowables) {
        field.addContent(Set.class)
                .addContent(".of(");
        if (listOfThrowables.isEmpty()) {
            field.addContent(")");
            return;
        }
        field.addContentLine("");
        field.increaseContentPadding()
                .increaseContentPadding()
                .update(it -> {
                    Iterator<TypeName> iterator = listOfThrowables.iterator();
                    while (iterator.hasNext()) {
                        it.addContent(iterator.next())
                                .addContent(".class");
                        if (iterator.hasNext()) {
                            it.addContent(",");
                        }
                        it.addContentLine("");
                    }
                })
                .decreaseContentPadding()
                .decreaseContentPadding()
                .addContent(")");
    }

    private TypeName fallbackMethodType(TypeName returnType, TypeName enclosingType) {
        return TypeName.builder(TypeName.create("io.helidon.faulttolerance.FallbackMethod"))
                .addTypeArgument(returnType.boxed())
                .addTypeArgument(enclosingType)
                .build();
    }

    private TypeName generatedType(TypeName enclosingType, String methodName) {
        return TypeName.builder()
                .packageName(enclosingType.packageName())
                .className(enclosingType.classNameWithEnclosingNames().replace('.', '_') + "_"
                                   + methodName + "__Fallback")
                .build();
    }

    private record BadCandidate(TypedElementInfo element, String reason) {
    }

}
