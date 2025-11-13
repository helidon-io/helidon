package io.helidon.builder.codegen;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

public class GeneratedFactoryMethod implements GeneratedMethod {
    private final TypedElementInfo method;
    private final Consumer<ContentBuilder<?>> consumer;

    public GeneratedFactoryMethod(TypedElementInfo method, Consumer<ContentBuilder<?>> consumer) {
        this.method = method;
        this.consumer = consumer;
    }

    public static GeneratedFactoryMethod create(TypeName declaringType,
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

        return new GeneratedFactoryMethod(newOne, content);
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
