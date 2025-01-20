package io.helidon.integrations.langchain4j.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.codegen.spi.CodegenExtensionProvider;
import io.helidon.common.types.TypeName;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_SERVICE;

public class AiServiceCodegenProvider implements CodegenExtensionProvider {
    public AiServiceCodegenProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(AI_SERVICE);
    }

    @Override
    public CodegenExtension create(CodegenContext ctx, TypeName generator) {
        return new AiServiceCodegen(ctx);
    }
}
