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
 * Injection Annotation Processor module.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
module io.helidon.inject.processor {

    exports io.helidon.inject.processor.spi;
    exports io.helidon.inject.processor;
    requires io.helidon.builder.api;
    requires io.helidon.common.processor;
    requires io.helidon.common;

    requires transitive io.helidon.inject.tools;
    requires transitive java.compiler;

    uses io.helidon.inject.processor.spi.InjectionAnnotationProcessorObserver;
    uses io.helidon.inject.tools.spi.InterceptorCreator;
    uses io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

    provides javax.annotation.processing.Processor with
            io.helidon.inject.processor.CustomAnnotationProcessor,
            io.helidon.inject.processor.UnsupportedConstructsProcessor,
            io.helidon.inject.processor.InjectionAnnotationProcessor;

}
