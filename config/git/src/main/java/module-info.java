/*
 * Copyright (c) 2017, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * config git module.
 */
@Features.Name("git")
@Features.Description("Config source based on a git repository")
@Features.Flavor({HelidonFlavor.MP, HelidonFlavor.SE})
@Features.Path({"Config", "git"})
module io.helidon.config.git {

    requires io.helidon.common.media.type;
    requires io.helidon.common;
    requires io.helidon.config;
    requires org.eclipse.jgit;

    requires static io.helidon.common.features.api;

    exports io.helidon.config.git;

    provides io.helidon.config.spi.ConfigSourceProvider with io.helidon.config.git.GitConfigSourceProvider;

}
