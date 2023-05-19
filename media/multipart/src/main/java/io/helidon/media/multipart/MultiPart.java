/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.media.multipart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Multipart entity.
 *
 * @param <T> body part type
 * @see ReadableMultiPart
 * @see WriteableMultiPart
 * @deprecated Use {@code content().asStream(ReadableBodyPart.class)} instead to read multipart entities.
 * This interface will be removed at 3.0.0 version.
 */
@Deprecated(since = "2.5.0", forRemoval = true)
public interface MultiPart<T extends BodyPart> {

    /**
     * Get all the nested body parts.
     *
     * @return list of {@link BodyPart}
     */
    List<T> bodyParts();

    /**
     * Get the first body part identified by the given control name. The control
     * name is the {@code name} parameter of the {@code Content-Disposition}
     * header for a body part with disposition type {@code form-data}.
     *
     * @param name control name
     * @return {@code Optional<BodyPart>}, never {@code null}
     */
    default Optional<T> field(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (T part : bodyParts()) {
            String partName = part.name();
            if (partName == null) {
                continue;
            }
            if (name.equals(partName)) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the body parts identified by the given control name. The control
     * name is the {@code name} parameter of the {@code Content-Disposition}
     * header for a body part with disposition type {@code form-data}.
     *
     * @param name control name
     * @return {@code List<BodyPart>}, never {@code null}
     */
    default List<T> fields(String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (T part : bodyParts()) {
            String partName = part.name();
            if (partName == null) {
                continue;
            }
            if (partName.equals(name)) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Get all the body parts that are identified with form data control names.
     * @return map of control names to body parts,never {@code null}
     */
    default Map<String, List<T>> fields() {
        Map<String, List<T>> results = new HashMap<>();
        for (T part : bodyParts()) {
            String name = part.name();
            if (name == null) {
                continue;
            }
            results.computeIfAbsent(name, n -> new ArrayList<>())
                   .add(part);
        }
        return results;
    }
}
