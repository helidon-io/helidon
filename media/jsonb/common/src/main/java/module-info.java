/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.media.common.spi.MediaServiceProvider;
import io.helidon.media.jsonb.common.JsonbProvider;
import io.helidon.media.jsonb.common.JsonbSupport;

/**
 * JSON-B support common classes.
 *
 * @see JsonbSupport
 */
module io.helidon.media.jsonb.common {

    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.config;
    requires io.helidon.media.common;
    requires transitive java.json.bind;

    exports io.helidon.media.jsonb.common;

    provides MediaServiceProvider with JsonbProvider;
}
