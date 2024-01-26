package io.helidon.faulttolerance.codegen;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectionCodegenContext;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;
import io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider;

public class FallbackExtensionProvider implements InjectCodegenExtensionProvider {
    static final TypeName FALLBACK_ANNOTATION = TypeName.create("io.helidon.faulttolerance.FaultTolerance.Fallback");

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(FALLBACK_ANNOTATION);
    }

    @Override
    public InjectCodegenExtension create(InjectionCodegenContext codegenContext) {
        return new FallbackExtension(codegenContext);
    }
}
