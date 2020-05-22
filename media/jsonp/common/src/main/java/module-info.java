/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import io.helidon.media.common.spi.MediaSupportProvider;
import io.helidon.media.jsonp.common.JsonpProvider;
import io.helidon.media.jsonp.common.JsonpSupport;

/**
 * JSON-P support common classes.
 *
 * @see JsonpSupport
 */
module io.helidon.media.jsonp.common {

    requires io.helidon.common;
    requires io.helidon.common.http;
    requires io.helidon.common.mapper;
    requires io.helidon.common.reactive;
    requires io.helidon.config;
    requires io.helidon.media.common;
    requires transitive java.json;

    exports io.helidon.media.jsonp.common;

    provides MediaSupportProvider with JsonpProvider;
}
