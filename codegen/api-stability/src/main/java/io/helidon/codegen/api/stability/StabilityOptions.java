/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
