package io.helidon.declarative.codegen;

import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

abstract class AbstractParametersProvider {
    void codegenFromParameters(ContentBuilder<?> contentBuilder, TypeName parameterType, String paramName) {
        if (parameterType.isOptional()) {
            TypeName realType = parameterType.typeArguments().getFirst();
            // optional
            contentBuilder
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContent("\").");
            asMethod(contentBuilder, realType);
            contentBuilder.addContent(".asOptional();");
        } else if (parameterType.isList()) {
            TypeName realType = parameterType.typeArguments().getFirst();
            // list
            contentBuilder
                    .addContent(".allValues(\"")
                    .addContent(paramName)
                    .addContentLine("\")")
                    .addContentLine(".stream()")
                    .addContent(".map(helidonDeclarative__it -> helidonDeclarative__it.");
            getMethod(contentBuilder, realType);
            contentBuilder.addContentLine(")")
                    .addContent(".collect(")
                    .addContent(Collectors.class)
                    .addContent(".toList());");
        } else {
            // direct type
            contentBuilder
                    .addContent(".first(\"")
                    .addContent(paramName)
                    .addContent("\").");
            getMethod(contentBuilder, parameterType);
            contentBuilder.addContent(";");
        }

    }

    void asMethod(ContentBuilder<?> content, TypeName type) {
        TypeName boxed = type.boxed();

        if (TypeNames.BOXED_BOOLEAN.equals(boxed)) {
            content.addContent("asBoolean()");
            return;
        }

        if (TypeNames.BOXED_DOUBLE.equals(boxed)) {
            content.addContent("asDouble()");
            return;
        }

        if (TypeNames.BOXED_INT.equals(boxed)) {
            content.addContent("asInt()");
            return;
        }

        if (TypeNames.BOXED_LONG.equals(boxed)) {
            content.addContent("asLong()");
            return;
        }

        if (TypeNames.STRING.equals(type)) {
            content.addContent("asString()");
            return;
        }

        content.addContent("as(")
                .addContent(boxed)
                .addContent(".class)");
    }

    void getMethod(ContentBuilder<?> content, TypeName type) {
        TypeName boxed = type.boxed();

        if (TypeNames.BOXED_BOOLEAN.equals(boxed)) {
            content.addContent("getBoolean()");
            return;
        }

        if (TypeNames.BOXED_DOUBLE.equals(boxed)) {
            content.addContent("getDouble()");
            return;
        }

        if (TypeNames.BOXED_INT.equals(boxed)) {
            content.addContent("getInt()");
            return;
        }

        if (TypeNames.BOXED_LONG.equals(boxed)) {
            content.addContent("getLong()");
            return;
        }

        if (TypeNames.STRING.equals(type)) {
            content.addContent("getString()");
            return;
        }

        content.addContent("get(")
                .addContent(boxed)
                .addContent(".class)");
    }
}
