/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.Preview;
import io.helidon.common.resumable.ResumableSupport;

/**
 * Helidon abstraction over CRaC API, replaces no-op implementation when this module is present on classpath.
 */
@Preview
@Feature(value = "CRaC",
         description = "Coordinated Restore at Checkpoint",
         in = {HelidonFlavor.MP, HelidonFlavor.SE},
         path = {"CRaC"},
         since = "4.2.0"
)
module io.helidon.integrations.crac {
    requires static io.helidon.common.features.api; // @Feature
    requires transitive crac;
    requires io.helidon.common.resumable;
    provides ResumableSupport with io.helidon.integrations.crac.CracSupport;
}