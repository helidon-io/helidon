package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

class GeneratedMethods {
    private GeneratedMethods() {
    }

    static GeneratedMethod createPrototypeMethod(TypeName declaringType,
                                                 TypedElementInfo referenceMethod,
                                                 List<Annotation> annotations) {

        var list = referenceMethod.parameterArguments();
        if (list.isEmpty()) {
            throw new CodegenException("Custom prototype method must have at least one parameter - the prototype itself",
                                       referenceMethod);
        }

        var newList = new ArrayList<>(list);
        // remove the first parameter
        newList.removeFirst();

        List<String> paramNames = newList.stream()
                .map(TypedElementInfo::elementName)
                .toList();

        Javadoc javadoc = Javadoc.parse(referenceMethod.description().orElse(""));
        var originalParamsJavadoc = javadoc.parameters();
        Map<String, List<String>> newParamsJavadoc = new LinkedHashMap<>();
        for (String paramName : paramNames) {
            var doc = originalParamsJavadoc.get(paramName);
            if (doc != null) {
                newParamsJavadoc.put(paramName, doc);
            }
        }
        Javadoc newJavadoc = Javadoc.builder()
                .from(javadoc)
                .parameters(newParamsJavadoc)
                .build();

        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .clearEnclosingType()
                .parameterArguments(newList)
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .modifiers(Set.of())
                .elementModifiers(Set.of(Modifier.DEFAULT))
                .build();

        Consumer<ContentBuilder<?>> content = cb -> {
            if (!newOne.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                cb.addContent("return ");
            }
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(this");

            if (!paramNames.isEmpty()) {
                cb.addContent(", ");
            }

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");");
        };

        return GeneratedMethod.builder()
                .javadoc(newJavadoc)
                .method(newOne)
                .contentBuilder(content)
                .build();
    }

    static GeneratedMethod createFactoryMethod(TypeName declaringType,
                                               TypedElementInfo referenceMethod,
                                               List<Annotation> annotations) {
        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .clearEnclosingType()
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .elementModifiers(Set.of(Modifier.STATIC))
                .build();

        List<String> paramNames = referenceMethod.parameterArguments()
                .stream()
                .map(TypedElementInfo::elementName)
                .toList();

        Consumer<ContentBuilder<?>> content = cb -> {
            if (!newOne.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                cb.addContent("return ");
            }
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(");

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");");
        };

        return GeneratedMethod.builder()
                .javadoc(Javadoc.parse(referenceMethod.description().orElse("")))
                .method(newOne)
                .contentBuilder(content)
                .build();
    }

    static GeneratedMethod createBuilderMethod(TypeName declaringType,
                                               TypedElementInfo referenceMethod,
                                               List<Annotation> annotations) {

        var list = referenceMethod.parameterArguments();
        if (list.isEmpty()) {
            throw new CodegenException("Custom builder method must have at least one parameter - the builder base itself",
                                       referenceMethod);
        }

        var newList = new ArrayList<>(list);
        // remove the first parameter
        newList.removeFirst();

        List<String> paramNames = newList.stream()
                .map(TypedElementInfo::elementName)
                .toList();

        Javadoc javadoc = Javadoc.parse(referenceMethod.description().orElse(""));
        var originalParamsJavadoc = javadoc.parameters();
        Map<String, List<String>> newParamsJavadoc = new LinkedHashMap<>();
        for (String paramName : paramNames) {
            var doc = originalParamsJavadoc.get(paramName);
            if (doc != null) {
                newParamsJavadoc.put(paramName, doc);
            }
        }
        Javadoc newJavadoc = Javadoc.builder()
                .from(javadoc)
                .parameters(newParamsJavadoc)
                .returnDescription("updated builder instance")
                .build();

        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .typeName(Utils.builderReturnType())
                .clearEnclosingType()
                .parameterArguments(newList)
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .modifiers(Set.of())
                .elementModifiers(Set.of())
                .build();

        Consumer<ContentBuilder<?>> content = cb -> {
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(this");

            if (!paramNames.isEmpty()) {
                cb.addContent(", ");
            }

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");")
                    .addContentLine("return self();");

        };

        return GeneratedMethod.builder()
                .javadoc(newJavadoc)
                .method(newOne)
                .contentBuilder(content)
                .build();

    }
}
