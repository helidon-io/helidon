package io.helidon.faulttolerance.codegen;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.ServiceCodegenContext;
import io.helidon.service.codegen.spi.InjectCodegenExtension;
import io.helidon.service.codegen.spi.InjectCodegenExtensionProvider;

public class FallbackExtensionProvider implements InjectCodegenExtensionProvider {
    static final TypeName FALLBACK_ANNOTATION = TypeName.create("io.helidon.faulttolerance.FaultTolerance.Fallback");

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(FALLBACK_ANNOTATION);
    }

    @Override
    public InjectCodegenExtension create(ServiceCodegenContext codegenContext) {
        return new FallbackExtension(codegenContext);
    }
}
