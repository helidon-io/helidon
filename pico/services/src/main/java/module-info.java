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

import io.helidon.pico.Application;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServices;

/**
 * The reference implementation of the Helidon Pico SPI.
 */
module io.helidon.pico {
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires static io.helidon.pico.api;
    requires static io.helidon.pico.builder.api;

    requires transitive io.helidon.pico.spi;

    exports io.helidon.pico.spi.ext;
    exports io.helidon.pico.spi.impl
            to io.helidon.pico.tools, io.helidon.pico.maven.plugin, io.helidon.pico.test.support, io.helidon.pico.test.resources;

    provides PicoServices with io.helidon.pico.spi.impl.DefaultPicoServices;

    uses Module;
    uses Application;
}
