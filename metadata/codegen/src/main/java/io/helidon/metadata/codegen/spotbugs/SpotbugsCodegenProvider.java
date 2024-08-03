package io.helidon.metadata.codegen.spotbugs;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.TypeName;

import static io.helidon.metadata.codegen.spotbugs.SpotbugsTypes.EXCLUDE;

public class SpotbugsCodegenProvider implements CodegenExtensionProvider {
    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new SpotbugsCodegen(ctx);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(EXCLUDE);
    }
}
