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

import io.helidon.pico.tools.spi.ActivatorCreator;
import io.helidon.pico.tools.spi.ApplicationCreator;
import io.helidon.pico.tools.spi.ExternalModuleCreator;
import io.helidon.pico.tools.spi.InterceptorCreator;

/**
 * The Pico Tools module.
 */
module io.helidon.pico.tools {
    requires static io.helidon.config.metadata;
    requires static jakarta.annotation;

    requires java.compiler;
    requires jakarta.inject;
    requires handlebars;
    requires io.github.classgraph;
    requires io.helidon.builder;
    requires io.helidon.common;
    requires io.helidon.common.config;
    requires io.helidon.builder.processor.spi;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.pico.runtime;
    requires transitive io.helidon.builder.processor.tools;

    exports io.helidon.pico.tools;
    exports io.helidon.pico.tools.spi;

    uses io.helidon.pico.tools.spi.ActivatorCreator;
    uses io.helidon.pico.tools.spi.ApplicationCreator;
    uses io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator;
    uses io.helidon.pico.tools.spi.ExternalModuleCreator;
    uses io.helidon.pico.tools.spi.InterceptorCreator;

    provides ActivatorCreator
            with io.helidon.pico.tools.DefaultActivatorCreator;
    provides ApplicationCreator
            with io.helidon.pico.tools.DefaultApplicationCreator;
    provides ExternalModuleCreator
            with io.helidon.pico.tools.DefaultExternalModuleCreator;
    provides InterceptorCreator
            with io.helidon.pico.tools.DefaultInterceptorCreator;
}
