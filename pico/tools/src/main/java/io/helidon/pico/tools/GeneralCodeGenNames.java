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

package io.helidon.pico.tools;

import java.util.Optional;

import io.helidon.builder.Builder;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * General code gen information.
 */
@Builder
public interface GeneralCodeGenNames {

    /**
     * Optionally, the name of the template to apply, defaulting to "default".
     *
     * @return the template name that should be used
     */
    @ConfiguredOption("default")
    String templateName();

    /**
     * The module name, defaulting to "unnamed" if not specified.
     * This name is used primarily to serve as the codegen name for the {@link io.helidon.pico.Module} that is generated.
     *
     * @return module name
     */
//    @ConfiguredOption("unnamed")
    Optional<String> moduleName();

    /**
     * The package name to use for the generated {@link io.helidon.pico.Module}, {@link io.helidon.pico.Application}, etc.
     * If one is not provided, one will be determined internally.
     *
     * @return the suggested package name, otherwise passing null will delegate package naming to the implementation heuristic
     */
    Optional<String> packageName();

}
