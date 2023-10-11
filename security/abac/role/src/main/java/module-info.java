/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Role attribute validator.
 */
@Feature(value = "Role",
        description = "ABAC Role based attribute validator",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Security", "Provider", "ABAC", "Role"}
)
module io.helidon.security.abac.role {

    requires io.helidon.security.providers.abac;
    requires jakarta.annotation;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.common.config;
    requires transitive io.helidon.security.providers.common;
    requires transitive io.helidon.security;

    exports io.helidon.security.abac.role;

    provides io.helidon.security.providers.common.spi.AnnotationAnalyzer
            with io.helidon.security.abac.role.RoleAnnotationAnalyzer;
    provides io.helidon.security.providers.abac.spi.AbacValidatorService
            with io.helidon.security.abac.role.RoleValidatorService;

    uses io.helidon.security.providers.common.spi.AnnotationAnalyzer;

}
