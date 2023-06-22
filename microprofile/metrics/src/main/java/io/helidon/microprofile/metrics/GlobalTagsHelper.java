/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.micrometer.core.instrument.Tag;

/**
 * Manages retrieving and dispensing global tags.
 * <p>
 *     The internal tags data starts as Optional.empty(). Once {@link #globalTags(String)} has been invoked the internal tags
 *     data Optional will no longer be empty, but the Tag[] it holds might be zero-length if there are no global tags to be
 *     concerned with.
 * </p>
 */
class GlobalTagsHelper {

    // Static instance is used normally at runtime to share a single set of global tags across possibly multiple metric
    // registries. Otherwise, regular instances of the helper are typically created only for testing.
    private static final GlobalTagsHelper INSTANCE = new GlobalTagsHelper();

    private Optional<Tag[]> tags = Optional.empty();

    /**
     * Sets the tags for normal use.
     *
     * @param tagExpression tag assignments
     */
    static void globalTags(String tagExpression) {
        INSTANCE.tags(tagExpression);
    }

    /**
     * Retrieves the tags for normal use.
     *
     * @return tags derived from the earlier assignment
     */
    static Optional<Tag[]> globalTags() {
        return INSTANCE.tags();
    }

    /**
     * For testing or internal use; sets the tags according to the comma-separate list of "name=value" settings.
     * <p>
     *     The expression can contain escaped "=" and "," characters.
     * </p>
     *
     * @param tagExpression  tag assignments
     */
    Optional<Tag[]> tags(String tagExpression) {

        // The following regex splits on non-escaped commas.
        String[] assignments = tagExpression.split("(?<!\\\\),");

        Tag[] result = new Tag[assignments.length];
        int resultCount = 0;
        int position = 0;

        List<String> problems = new ArrayList<>();

        for (String assignment : assignments) {
            List<String> assignmentProblems = new ArrayList<>();
            if (assignment.isBlank()) {
                assignmentProblems.add("empty assignment found at position " + position + ": " + tagExpression);
            } else {

                // The following regex splits on non-escaped equals signs.
                String[] parts = assignment.split("(?<!\\\\)=");

                if (parts.length != 2) {
                    assignmentProblems.add("expected 2 parts separated by =; found " + parts.length);
                } else {
                    String name = parts[0];
                    String value = parts[1];
                    if (name.isBlank()) {
                        assignmentProblems.add("left side of assignment is blank");
                    }
                    if (value.isBlank()) {
                        assignmentProblems.add("right side of assignment is blank");
                    }
                    if (!name.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                        assignmentProblems.add(
                                "tag name must start with a letter and include only letters, digits, and underscores");
                    }
                    if (assignmentProblems.isEmpty()) {
                        result[resultCount++] = Tag.of(name,
                                                       value.replace("\\,", ",")
                                                               .replace("\\=", "="));
                    }
                }
            }
            if (!assignmentProblems.isEmpty()) {
                problems.add("position " + position + ": " + assignmentProblems);
            }

            position++;
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Error(s) in global tag assignment: " + problems);
        }

        tags = Optional.of(result);
        return tags;
    }

    /**
     * For testing or internal use; returns the tags derived from the assignment expression.
     *
     * @return tags
     */
    Optional<Tag[]> tags() {
        return tags;
    }
}
