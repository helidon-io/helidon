package io.helidon.builder.codegen;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.types.TypeName;

public interface PrototypeConstant {
    String name();

    TypeName type();

    Javadoc javadoc();

    void accept(ContentBuilder<?> content);
}
