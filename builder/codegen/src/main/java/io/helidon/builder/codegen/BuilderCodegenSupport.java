package io.helidon.builder.codegen;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.classmodel.Javadoc;

final class BuilderCodegenSupport {
    private BuilderCodegenSupport() {
    }

    static class GeneratedMethodDecorator implements Prototype.BuilderDecorator<GeneratedMethod.BuilderBase<?, ?>> {
        GeneratedMethodDecorator() {
        }

        @Override
        public void decorate(GeneratedMethod.BuilderBase<?, ?> target) {
            if (target.method().isPresent() && target.javadoc().isEmpty()) {
                target.javadoc(Javadoc.parse(target.method().get().description().orElse("")));
            }
        }
    }
}
