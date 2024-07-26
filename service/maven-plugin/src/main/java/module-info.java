/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Injection maven-plugin module.
 */
module io.helidon.service.maven.plugin {

    requires io.helidon.builder.api;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.codegen;
    requires io.helidon.codegen.scan;
    requires io.helidon.codegen.compiler;
    requires io.helidon.service.codegen;
    requires io.helidon.service.inject.api;

    requires maven.artifact;
    requires maven.model;
    requires maven.plugin.annotations;
    requires maven.plugin.api;
    requires maven.project;
    requires io.github.classgraph;

    exports io.helidon.service.maven.plugin;
}
