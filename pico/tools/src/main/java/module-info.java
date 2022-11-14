/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

module io.helidon.pico.tools {
    requires static java.compiler;
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires static com.fasterxml.jackson.annotation;
    requires static java.annotation;
    requires static lombok;
    requires transitive io.helidon.pico.api;
    requires transitive io.helidon.pico.builder.api;

    requires transitive io.helidon.pico;

    uses io.helidon.pico.tools.creator.ActivatorCreator;

    requires handlebars;
    requires io.github.classgraph;
    requires io.helidon.pico.builder.tools;

    exports io.helidon.pico.tools;
    exports io.helidon.pico.tools.creator;
    exports io.helidon.pico.tools.processor;
    exports io.helidon.pico.tools.types;
    exports io.helidon.pico.tools.utils
            to io.helidon.pico.processor, io.helidon.pico.maven.plugin, io.helidon.pico.test.pico;
    exports io.helidon.pico.tools.creator.impl
            to io.helidon.pico.processor, io.helidon.pico.maven.plugin;

    provides io.helidon.pico.tools.processor.JavaxTypeTools with
            io.helidon.pico.tools.processor.impl.JavaxTypeToolsImpl;

    provides io.helidon.pico.tools.creator.ActivatorCreator
            with io.helidon.pico.tools.creator.impl.DefaultActivatorCreator;
    provides io.helidon.pico.tools.creator.ApplicationCreator
            with io.helidon.pico.tools.creator.impl.DefaultApplicationCreator;
    provides io.helidon.pico.tools.creator.ExternalModuleCreator
            with io.helidon.pico.tools.creator.impl.DefaultExternalModuleCreator;
    provides io.helidon.pico.tools.creator.InterceptorCreator
            with io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator;

}
