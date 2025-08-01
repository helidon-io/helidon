/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import io.helidon.common.features.api.HelidonFlavor;

import static io.helidon.common.features.api.Features.*;

/**
 * Jackson media support.
 */
@Name("Jackson")
@Description("Jackson media support")
@Flavor(HelidonFlavor.SE)
@Path({"Media", "Jackson"})
module io.helidon.http.media.jackson {

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.module.paramnames;
    requires io.helidon.http.media;

    requires static io.helidon.common.features.api;

    exports io.helidon.http.media.jackson;

    provides io.helidon.http.media.spi.MediaSupportProvider
            with io.helidon.http.media.jackson.JacksonMediaSupportProvider;

}