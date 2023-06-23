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
 * The Builder Processor SPI module provides these definitions:
 * <ol>
 *     <li>{@link io.helidon.builder.processor.spi.BuilderCreatorProvider} - responsible for code generating the
 *     implementation w/ a fluent builder.</li>
 *     <li>{@link io.helidon.builder.processor.spi.TypeInfoCreatorProvider} - responsible for code generating the model
 *     object for the target interface.</li>
 *     <li>{@link io.helidon.builder.processor.spi.TypeAndBody} - the dom-like description of the target type of the
 *     builder.</li>
 * </ol>
 */
package io.helidon.builder.processor.spi;
