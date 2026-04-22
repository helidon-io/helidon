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
 * Helidon implementation of the
 * <a href="https://github.com/FasterXML/smile-format-specification">Jackson Smile binary JSON specification</a>.
 */
@Features.Name("JSON Smile")
@Features.Description("Jackson Smile binary JSON implementation")
@Features.Flavor(HelidonFlavor.SE)
@Features.Path({"JSON", "Smile"})
module io.helidon.json.smile {

    requires static io.helidon.common.features.api;

    requires io.helidon.json;
    requires io.helidon.common.buffers;
    requires io.helidon.builder.api;

    exports io.helidon.json.smile;
}
