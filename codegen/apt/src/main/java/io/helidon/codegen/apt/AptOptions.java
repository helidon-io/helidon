/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.codegen.apt;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;

class AptOptions implements CodegenOptions {
    private final ProcessingEnvironment aptEnv;

    AptOptions(ProcessingEnvironment aptEnv) {
        this.aptEnv = aptEnv;
    }

    static CodegenOptions create(ProcessingEnvironment env) {
        return new AptOptions(env);
    }

    @Override
    public Optional<String> option(String option) {
        return Optional.ofNullable(aptEnv.getOptions().get(option));
    }

    @Override
    public void validate(Set<Option<?>> permittedOptions) {
        Set<String> helidonOptions = aptEnv.getOptions()
                .keySet()
                .stream()
                .filter(it -> it.startsWith("helidon."))
                .collect(Collectors.toSet());

        // now remove all expected
        permittedOptions.stream()
                .map(Option::name)
                .forEach(helidonOptions::remove);

        helidonOptions.remove(CODEGEN_SCOPE.name());
        helidonOptions.remove(CODEGEN_MODULE.name());
        helidonOptions.remove(CODEGEN_PACKAGE.name());
        helidonOptions.remove(INDENT_TYPE.name());
        helidonOptions.remove(INDENT_COUNT.name());
        helidonOptions.remove(CREATE_META_INF_SERVICES.name());

        if (!helidonOptions.isEmpty()) {
            throw new CodegenException("Unrecognized/unsupported Helidon option configured: " + helidonOptions);
        }
    }
}
