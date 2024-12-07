/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.maven.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;

class MavenOptions implements CodegenOptions {
    private final Map<String, String> options;

    private MavenOptions(Map<String, String> options) {
        this.options = options;
    }

    static CodegenOptions create(Set<String> compilerArgs) {
        Map<String, String> options = new HashMap<>();

        compilerArgs.forEach(it -> addInjectOption(options, it));

        return new MavenOptions(Map.copyOf(options));
    }

    @Override
    public Optional<String> option(String option) {
        return Optional.ofNullable(options.get(option)).map(String::trim);
    }

    @Override
    public void validate(Set<Option<?>> permittedOptions) {
        Set<String> helidonOptions = options
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

    private static void addInjectOption(Map<String, String> options, String option) {
        String toProcess = option;
        if (toProcess.startsWith("-A")) {
            toProcess = toProcess.substring(2);
        }
        int eq = toProcess.indexOf('=');
        if (eq < 0) {
            options.put(toProcess, "true");
            return;
        }
        options.put(toProcess.substring(0, eq), toProcess.substring(eq + 1));
    }
}
