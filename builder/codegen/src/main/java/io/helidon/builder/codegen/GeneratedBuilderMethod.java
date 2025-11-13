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
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

public class GeneratedBuilderMethod implements GeneratedMethod {
    private final TypedElementInfo method;
    private final Consumer<ContentBuilder<?>> consumer;

    public GeneratedBuilderMethod(TypedElementInfo method, Consumer<ContentBuilder<?>> consumer) {
        this.method = method;
        this.consumer = consumer;
    }

    public static GeneratedBuilderMethod create(TypeName declaringType,
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
                .build();

        TypedElementInfo newOne = TypedElementInfo.builder()
                .from(referenceMethod)
                .clearEnclosingType()
                .description(newJavadoc.toString())
                .parameterArguments(newList)
                .accessModifier(AccessModifier.PUBLIC)
                .annotations(annotations)
                .elementModifiers(Set.of())
                .build();

        Consumer<ContentBuilder<?>> content = cb -> {
            cb.addContent(declaringType)
                    .addContent(".")
                    .addContent(referenceMethod.elementName())
                    .addContent("(this");

            if (paramNames.size() > 1) {
                cb.addContent(", ");
            }

            cb.addContent(String.join(", ", paramNames));

            cb.addContentLine(");")
                    .addContentLine("return self();");

        };

        return new GeneratedBuilderMethod(newOne, content);
    }

    @Override
    public TypedElementInfo methodDefinition() {
        return method;
    }

    @Override
    public void accept(ContentBuilder<?> contentBuilder) {
        consumer.accept(contentBuilder);
    }
}
