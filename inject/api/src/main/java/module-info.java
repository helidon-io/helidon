/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
 * Injection API module.
 */
module io.helidon.inject.api {

    requires io.helidon.common.config;
    requires io.helidon.common.types;
    requires io.helidon.common;
    requires io.helidon.logging.common;

    requires static jakarta.inject;
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;

    requires transitive io.helidon.builder.api;

    exports io.helidon.inject.api;
    exports io.helidon.inject.spi;

    uses io.helidon.inject.spi.InjectionServicesProvider;

}
