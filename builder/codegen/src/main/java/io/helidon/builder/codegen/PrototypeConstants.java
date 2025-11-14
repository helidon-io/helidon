package io.helidon.builder.codegen;

import java.util.function.Consumer;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

final class PrototypeConstants {
    private PrototypeConstants() {
    }

    public static PrototypeConstant create(TypeName declaringType,
                                           TypeName constantType,
                                           String constantName,
                                           Javadoc javadoc) {
        Consumer<ContentBuilder<?>> consumer = content -> {
            content.addContent(declaringType)
                    .addContent(".")
                    .addContent(constantName);
        };

        return PrototypeConstant.builder()
                .content(consumer)
                .name(constantName)
                .type(constantType)
                .javadoc(javadoc)
                .build();
    }

    public static PrototypeConstant create(TypeName constantType,
                                           String constantName,
                                           Javadoc javadoc,
                                           Consumer<ContentBuilder<?>> consumer) {

        return PrototypeConstant.builder()
                .content(consumer)
                .name(constantName)
                .type(constantType)
                .javadoc(javadoc)
                .build();
    }
}
