/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
 * The Injection Tools module.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
module io.helidon.inject.tools {

    exports io.helidon.inject.tools.spi;
    exports io.helidon.inject.tools;
    requires handlebars;
    requires io.github.classgraph;
    requires io.helidon.builder.api;
    requires io.helidon.common.config;
    requires io.helidon.common.processor;
    requires io.helidon.common;
    requires jakarta.inject;
    requires java.compiler;
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.inject.runtime;

    uses io.helidon.inject.tools.spi.InterceptorCreator;
    uses io.helidon.inject.tools.spi.ApplicationCreator;
    uses io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;
    uses io.helidon.inject.tools.spi.ExternalModuleCreator;
    uses io.helidon.inject.tools.spi.ActivatorCreator;
    uses io.helidon.inject.tools.spi.ModuleComponentNamer;

    provides io.helidon.inject.tools.spi.ActivatorCreator
            with io.helidon.inject.tools.ActivatorCreatorDefault;
    provides io.helidon.inject.tools.spi.ApplicationCreator
            with io.helidon.inject.tools.ApplicationCreatorDefault;
    provides io.helidon.inject.tools.spi.ExternalModuleCreator
            with io.helidon.inject.tools.ExternalModuleCreatorDefault;
    provides io.helidon.inject.tools.spi.InterceptorCreator
            with io.helidon.inject.tools.InterceptorCreatorDefault;
	
}
