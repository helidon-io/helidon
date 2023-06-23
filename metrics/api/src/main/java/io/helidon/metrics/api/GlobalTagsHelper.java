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
package io.helidon.metrics.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Manages retrieving and dispensing global tags.
 * <p>
 *     The internal tags data starts as Optional.empty(). Once {@link #globalTags(String)} has been invoked the internal tags
 *     data Optional will no longer be empty, but the Tag[] it holds might be zero-length if there are no global tags to be
 *     concerned with.
 * </p>
 */
public class GlobalTagsHelper {

    // Static instance is used normally at runtime to share a single set of global tags across possibly multiple metric
    // registries. Otherwise, regular instances of the helper are typically created only for testing.
    private static final GlobalTagsHelper INSTANCE = new GlobalTagsHelper();

    private Tag[] globalTags;
    private String scopeTagName;

    private GlobalTagsHelper() {
    }

    /**
     * Sets the tags for normal use.
     *
     * @param tagExpression tag assignments
     */
    public static void globalTags(String tagExpression) {
        INSTANCE.tags(tagExpression);
    }

    /**
     * Sets the tag name used for identifying the scope in metrics output.
     *
     * @param scopeTagName tag name for identifying a meter's scope
     */
    public static void scopeTagName(String scopeTagName) {
        INSTANCE.scopeTagName = scopeTagName;
    }

    /**
     * Prepares a {@link Tags} object accounting for the specified scope (if the scope tag name has been set),
     * any globally-set tags, and the indicated tags from the caller.
     *
     * @param scope scope to identify using a tag
     * @param tags tags otherwise specified for the meter
     * @return the {@code Tags} object reflecting the relevant tags
     */
    public static Tags augmentedTags(String scope, Iterable<Map.Entry<String, String>> tags) {
        return INSTANCE.augmentTags(scope, tags);
    }

    /**
     * Creates a new instance of the helper <em>without</em> also establishing it as the singleton instance.
     *
     * @return new instance
     */
    static GlobalTagsHelper create() {
        return new GlobalTagsHelper();
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

        globalTags = result;
        return tags();
    }

   /**
     * For testing or internal use; returns the tags derived from the assignment expression.
     *
     * @return tags
     */
    Optional<Tag[]> tags() {
        return globalTags == null || globalTags.length == 0
                ? Optional.empty()
                : Optional.of(globalTags);
    }

    private Tags augmentTags(String scope, Iterable<Map.Entry<String, String>> tags) {
        AtomicReference<Tags> result = new AtomicReference<>(Tags.empty());
        tags.forEach(tag -> result.set(result.get().and(tag.getKey(), tag.getValue())));
        if (scopeTagName != null) {
            result.set(result.get().and(Tag.of(scopeTagName, scope)));
        }
        if (globalTags != null && globalTags.length > 0) {
            result.set(result.get().and(globalTags));
        }
        return result.get();
    }
}
