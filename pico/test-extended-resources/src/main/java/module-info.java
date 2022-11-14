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

module io.helidon.pico.test.extended.resources {
    requires transitive io.helidon.pico.processor;
    requires static jakarta.inject;
    requires static jakarta.annotation;

    exports io.helidon.pico.test.extended.resources;
    exports io.helidon.pico.test.extended.resources.producer;

    requires io.helidon.common;
    requires java.compiler;
    requires io.helidon.pico;
    requires io.helidon.pico.test.utils;

    provides io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer
            with io.helidon.pico.test.extended.resources.producer.RunLevelResourceProducer,
                    io.helidon.pico.test.extended.resources.producer.MyCompileTimeProducer;
}
