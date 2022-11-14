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

import io.helidon.pico.tools.creator.ActivatorCreator;
import io.helidon.pico.tools.creator.ApplicationCreator;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreator;
import io.helidon.pico.tools.creator.impl.DefaultApplicationCreator;

module io.helidon.pico.tools {
    requires io.helidon.common;
    requires java.compiler;
    requires io.helidon.pico.api;
    requires io.helidon.pico;
    requires static lombok;
    requires static com.fasterxml.jackson.annotation;
    requires handlebars;

    exports io.helidon.pico.tools;
    exports io.helidon.pico.tools.impl to io.helidon.pico.processor;
    exports io.helidon.pico.tools.utils to io.helidon.pico.processor;
    exports io.helidon.pico.tools.utils.module to io.helidon.pico.processor, handlebars;
    exports io.helidon.pico.tools.utils.template to io.helidon.pico.processor;

    provides ActivatorCreator with DefaultActivatorCreator;
    provides ApplicationCreator with DefaultApplicationCreator;
}
