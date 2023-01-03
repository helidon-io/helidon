/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
 * Scope attribute validator.
 */
@Feature(value = "Scope",
        description = "ABAC Scope based attribute validator",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Security", "Provider", "ABAC", "Scope"}
)
module io.helidon.security.abac.scope {
    requires static io.helidon.common.features.api;

    requires io.helidon.security.providers.abac;

    exports io.helidon.security.abac.scope;

    provides io.helidon.security.providers.abac.spi.AbacValidatorService with io.helidon.security.abac.scope.ScopeValidatorService;
}
