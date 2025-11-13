package io.helidon.builder.codegen;

import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

public class PrototypeConstantSynthetic implements PrototypeConstant {
    private final TypeName type;
    private final String name;
    private final Consumer<ContentBuilder<?>> consumer;
    private final Javadoc javadoc;

    private PrototypeConstantSynthetic(TypeName type,
                                       String name,
                                       Javadoc javadoc,
                                       Consumer<ContentBuilder<?>> consumer) {
        this.type = type;
        this.name = name;
        this.consumer = consumer;
        this.javadoc = javadoc;
    }

    public static PrototypeConstantSynthetic create(TypeName constantType,
                                                    String constantName,
                                                    Javadoc javadoc,
                                                    Consumer<ContentBuilder<?>> consumer) {

        return new PrototypeConstantSynthetic(constantType,
                                              constantName,
                                              javadoc,
                                              consumer);
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
    public Javadoc javadoc() {
        return javadoc;
    }

    @Override
    public void accept(ContentBuilder<?> content) {
        this.consumer.accept(content);
    }
}
