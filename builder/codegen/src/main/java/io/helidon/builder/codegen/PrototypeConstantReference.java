package io.helidon.builder.codegen;

import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

public class PrototypeConstantReference implements PrototypeConstant {
    private final TypeName type;
    private final String name;
    private final Consumer<ContentBuilder<?>> consumer;
    private final Javadoc javadoc;

    private PrototypeConstantReference(TypeName type,
                                       String name,
                                       Consumer<ContentBuilder<?>> consumer, Javadoc javadoc) {
        this.type = type;
        this.name = name;
        this.consumer = consumer;
        this.javadoc = javadoc;
    }

    public static PrototypeConstantReference create(TypeName declaringType,
                                                    TypeName constantType,
                                                    String constantName,
                                                    Javadoc javadoc) {
        Consumer<ContentBuilder<?>> consumer = content -> {
            content.addContent(declaringType)
                    .addContent(".")
                    .addContent(constantName);
        };

        return new PrototypeConstantReference(constantType,
                                              constantName,
                                              consumer,
                                              javadoc);
    }

    @Override
    public Javadoc javadoc() {
        return javadoc;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public TypeName type() {
        return type;
    }

    @Override
    public void accept(ContentBuilder<?> content) {
        this.consumer.accept(content);
    }
}
