package io.helidon.webserver.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;

import static io.helidon.webserver.codegen.WebServerCodegenTypes.HTTP_PATH_ANNOTATION;

@Weight(Weighted.DEFAULT_WEIGHT + 10)
public class WebServerCodegenProvider implements CodegenExtensionProvider {
    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(HTTP_PATH_ANNOTATION);
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generatorType) {
        return new WebServerCodegenExtension(ctx);
    }
}
