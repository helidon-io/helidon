package io.helidon.codegen.api.stability;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;

class StabilityOptions implements CodegenOptions {
    private final ProcessingEnvironment env;

    StabilityOptions(ProcessingEnvironment env) {
        this.env = env;
    }

    @Override
    public Optional<String> option(String option) {
        return Optional.ofNullable(env.getOptions().get(option));
    }

    @Override
    public void validate(Set<Option<?>> permittedOptions) {
        Set<String> helidonOptions = env.getOptions()
                .keySet()
                .stream()
                .filter(it -> it.startsWith("helidon."))
                .collect(Collectors.toSet());

        // now remove all expected
        permittedOptions.stream()
                .map(Option::name)
                .forEach(helidonOptions::remove);

        if (!helidonOptions.isEmpty()) {
            throw new CodegenException("Unrecognized/unsupported Helidon option configured: " + helidonOptions);
        }
    }
}
