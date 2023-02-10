/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Pico maven-plugin module.
 */
module io.helidon.pico.maven.plugin {
    requires maven.plugin.annotations;
    requires maven.plugin.api;
    requires maven.project;
    requires maven.artifact;
    requires maven.model;
    requires io.helidon.builder.config;
    requires io.helidon.common;
    requires io.helidon.config;
    requires transitive io.helidon.pico.tools;

    uses io.helidon.pico.tools.ActivatorCreator;
    uses io.helidon.pico.tools.ApplicationCreator;
    uses io.helidon.pico.tools.ExternalModuleCreator;

    exports io.helidon.pico.maven.plugin;
}
