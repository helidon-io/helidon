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

import io.helidon.common.features.api.Features;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Helidon JSON Core.
 * Provides fundamental JSON processing capabilities.
 * <p>
 * This module is incubating. These APIs may change in any version of Helidon, including backward incompatible changes.
 */
@Features.Name("JSON")
@Features.Description("JSON processing")
@Features.Flavor(HelidonFlavor.SE)
module io.helidon.json {
    requires static io.helidon.common.features.api;

    requires io.helidon.common;
    requires io.helidon.common.buffers;

    exports io.helidon.json;

    /*
    This module cannot be marked as @Features.Incubating, because it is used as a transitive dependency of health,
    and will be used by metrics (and maybe other modules) as a replacement for Jakarta JSON-P.
    We do not want to warn the users about use of incubating module, that is used by us
    */
}
