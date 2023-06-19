package io.helidon.builder.processor;

import java.util.List;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

record MethodSignature(TypeName returnType, String name, List<TypeName> arguments) {
    public static MethodSignature create(TypedElementInfo info) {
        return new MethodSignature(info.typeName(),
                info.elementName(),
                info.parameterArguments().stream()
                        .map(TypedElementInfo::typeName)
                        .toList());
    }
}
