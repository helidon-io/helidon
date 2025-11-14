package io.helidon.builder.codegen;

import io.helidon.codegen.classmodel.ClassBase;

final class Utils {
    private Utils() {
    }

    static void addGeneratedMethod(ClassBase.Builder<?, ?> classModel,
                                   GeneratedMethod generatedMethod) {
        var methodInfo = generatedMethod.method();
        var javadoc = generatedMethod.javadoc();
        var contentBuilder = generatedMethod.contentBuilder();

        classModel.addMethod(method -> {
            method.from(methodInfo)
                    .javadoc(javadoc)
                    .update(contentBuilder::accept);
        });
    }
}
