/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import io.helidon.reactive.media.common.spi.MediaSupportProvider;
import io.helidon.reactive.media.jackson.JacksonProvider;
import io.helidon.reactive.media.jackson.JacksonSupport;

/**
 * Jackson support common classes.
 *
 * @see JacksonSupport
 */
@Feature(value = "Jackson",
        description = "Media support for Jackson",
        in = HelidonFlavor.SE,
        path = {"Media", "Jackson"}
)
module io.helidon.reactive.media.jackson {
    requires static io.helidon.common.features.api;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.module.paramnames;
    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.reactive.media.common;
    requires io.helidon.config;

    exports io.helidon.reactive.media.jackson;

    provides MediaSupportProvider with JacksonProvider;
}
