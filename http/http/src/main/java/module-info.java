/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

/**
 * Helidon Common HTTP classes.
 */
module io.helidon.http {

    requires static io.helidon.config.metadata;
    requires static io.helidon.service.registry;
    requires static io.helidon.common.types;

    requires transitive io.helidon.common.buffers;
    requires transitive io.helidon.common.configurable;
    requires transitive io.helidon.common.mapper;
    requires transitive io.helidon.common.media.type;
    requires transitive io.helidon.common.uri;
    requires transitive io.helidon.common;

    exports io.helidon.http;

}
