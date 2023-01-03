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

/**
 * The Builder API consists of a few annotations that can be used to create fluent builders for the types that use
 * {@link io.helidon.builder.Builder}, or otherwise one of its kind. The meta annotation
 * {@link io.helidon.builder.BuilderTrigger} is used to annotate the annotations that trigger custom-style builders.
 * <p>
 * The {@link io.helidon.builder.Builder} annotation typically is applied to the an interface type, but it can also
 * be used directly on annotation types as well. When applied, and if the annotation processor is applied for the builder-type
 * annotation then an implementation class is generated that supports the fluent-builder pattern for that type.
 * <p>
 * A few things to note:
 * <ul>
 *     <li>The target type that is annotated with the builder annotation should have all getter-like methods.</li>
 *     <li>Any static or default method will be ignored during APT processing.</li>
 * </ul>
 */
package io.helidon.builder;
