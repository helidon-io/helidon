package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.builder.codegen.spi.BuilderCodegenExtension;

record OptionHandler(OptionInfo option, TypeHandler typeHandler) {
    static OptionHandler create(List<BuilderCodegenExtension> extensions,
                                PrototypeInfo prototypeInfo,
                                OptionInfo option) {
        return new OptionHandler(option, TypeHandler.create(extensions, prototypeInfo, option));
    }
}
