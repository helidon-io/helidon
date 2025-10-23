/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.query;

import java.util.Iterator;
import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Entity property.
 */
@Prototype.Blueprint
@Prototype.CustomMethods(PropertySupport.class)
interface PropertyBlueprint {

    /**
     * {@link List} of property name elements.
     * Property name consists of individual elements separated by {@code '.'} character, e.g. {@code person.name}.
     *
     * @return {@link List} of property name elements
     */
    @Option.Singular
    List<CharSequence> nameParts();

    /**
     * Property name.
     * Builds new {@link CharSequence} from stored property name elements.
     *
     * @return the property name
     */
    default CharSequence name() {
        int size = nameParts().size() - 1;
        for (CharSequence element : nameParts()) {
            size += element.length();
        }
        StringBuilder sb = new StringBuilder(size);
        if (!nameParts().isEmpty()) {
            Iterator<CharSequence> i = nameParts().listIterator();
            sb.append(i.next());
            while (i.hasNext()) {
                sb.append('.')
                        .append(i.next());
            }
        }
        return sb;
    }

}
