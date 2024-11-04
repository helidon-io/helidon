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

package io.helidon.service.inject.api;

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.Annotation;

/**
 * Represents a qualifier annotation (a specific case of annotations, annotated with
 * {@link Injection.Qualifier}).
 *
 * @see Injection.Qualifier
 */
@Prototype.Blueprint
@Prototype.CustomMethods(QualifierSupport.CustomMethods.class)
interface QualifierBlueprint extends Annotation {
}
