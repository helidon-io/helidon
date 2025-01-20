package io.helidon.integrations.langchain4j.codegen;

import java.util.Set;

import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.codegen.spi.TypeMapperProvider;
import io.helidon.common.types.TypeName;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_TOOL;

public class LcToolsMapperProvider implements TypeMapperProvider {
    public LcToolsMapperProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(LC_TOOL);
    }

    @Override
    public TypeMapper create(CodegenOptions codegenOptions) {
        return new LcToolsMapper();
    }
}
