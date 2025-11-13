package io.helidon.builder.codegen;

record OptionHandler(OptionInfo option, TypeHandler typeHandler) {
    static OptionHandler create(PrototypeInfo prototypeInfo, OptionInfo option) {
        return new OptionHandler(option, TypeHandler.create(prototypeInfo, option));
    }
}
