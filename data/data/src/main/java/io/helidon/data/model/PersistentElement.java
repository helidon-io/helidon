/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.data.model;

//import io.micronaut.core.annotation.AnnotationMetadataProvider;
//import io.micronaut.core.naming.Named;
import io.helidon.data.annotation.AnnotationMetadataProvider;

/**
 * Shared interface for a persistent element whether it be a type or a property.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface PersistentElement extends /** Named, */ AnnotationMetadataProvider {
    /**
     * The persisted name is the fully qualified name including potential schema definitions.
     *
     * @return The persisted name.
     */
    String getPersistedName();
}
