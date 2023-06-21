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
package io.helidon.metrics.microprofile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micrometer.core.instrument.Tags;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Tags utility class.
 */
class MpTags {

    private static final Set<String> RESERVED_TAG_NAMES = new HashSet<>();
    private static String mpAppValue;

    static {
        RESERVED_TAG_NAMES.add(MpMetricRegistry.MP_APPLICATION_TAG_NAME);
        RESERVED_TAG_NAMES.add(MpMetricRegistry.MP_SCOPE_TAG_NAME);
    }

    /**
     * Hidden constructor for utility class.
     */
    private MpTags() {
    }

    /**
     * Returns the "app" value.
     *
     * @return the app value
     */
    static String mpAppValue() {
        return mpAppValue;
    }

    /**
     * Returns the configuration used to prepare tags for metric IDs.
     *
     * @param config config used in setting tags in the IDs
     */
    static void config(Config config) {
        ConfigValue configValue = config.getConfigValue(MpMetricRegistry.MP_APPLICATION_TAG_NAME);
        if (configValue.getValue() != null) {
            mpAppValue = configValue.getValue();
        }
    }

    /**
     * Converts MicroProfile tags to the underlying implementation {@link io.micrometer.core.instrument.Tags} abstraction.
     *
     * @param tags MicroProfile tags to convert
     * @return underlying implementation tags abstraction
     */
    static Tags fromMp(List<Tag> tags) {
        return Tags.of(tags.stream()
                               .map(MpTags::fromMp)
                               .toList());
    }

    /**
     * Converts MicroProfile tags to the underlying implementation {@link io.micrometer.core.instrument.Tags} abstraction.
     *
     * @param tags MicroProfile tags to convert
     * @return underlying implementation tags abstraction
     */
    static Tags fromMp(Tag... tags) {
        return Tags.of(Arrays.stream(tags).map(MpTags::fromMp).toList());
    }

    /**
     * Converts MicroProfile tags to the underlying implementation {@link io.micrometer.core.instrument.Tags} abstraction.
     *
     * @param tags MicroProfile tags to convert
     * @return underlying implementation tags abstraction
     */
    static Tags fromMp(Map<String, String> tags) {
        return tags.entrySet().stream().collect(Tags::empty,
                                         (meterTags, entry) -> meterTags.and(entry.getKey(), entry.getValue()),
                                         Tags::and);
    }

    /**
     * Converts a MicroProfile tag to the underlying implementation {@link Tags} abstraction.
     *
     * @param tag MicroProfile tag to convert
     * @return underlying implementation tags abstraction
     */
    static io.micrometer.core.instrument.Tag fromMp(Tag tag) {
        return io.micrometer.core.instrument.Tag.of(tag.getTagName(), tag.getTagValue());
    }

    /**
     * Checks that the tag names in the provided metric ID are consistent with those in the provided list of metric IDs, throwing
     * an exception if not.
     *
     * @param mpMetricId the metric ID to check for consistent tag names
     * @param mpMetricIds other metric IDs
     */
    static void checkTagNameSetConsistency(MetricID mpMetricId, List<MetricID> mpMetricIds) {
        if (mpMetricIds == null || mpMetricIds.isEmpty()) {
            return;
        }

        for (MetricID id : mpMetricIds) {
            if (!isConsistentTagNameSet(id, mpMetricId)) {
                throw new IllegalArgumentException(String.format("""
                     Provided tag names are inconsistent with tag names on previously-registered metric with the same name %s; \
                     previous: %s, requested: %s""",
                                                                 mpMetricId.getName(),
                                                                 id.getTags().keySet(),
                                                                 mpMetricId.getTags().keySet()));
            }
        }
    }

    /**
     * Verifies that the candidate tag names are consistent with the specified groups of tags (typically tags previously recorded
     * for meters with the same name).
     *
     * @param metricName name of the meter ID being checked (for error reporting)
     * @param candidateTags candidate tags
     * @param establishedTagGroups groups of tags previously established against which to check the candidates
     * @throws java.lang.IllegalArgumentException if the candidate tags are not consistent with the established tag groups
     */
    static void checkTagNameSetConsistency(String metricName, Set<Tag> candidateTags, Iterable<Set<Tag>> establishedTagGroups) {
        for (Set<Tag> establishedTags : establishedTagGroups) {
            if (!candidateTags.equals(establishedTags)) {
                throw new IllegalArgumentException(String.format("""
                     Provided tag names are inconsistent with tag names on previously-registered metric with the same name %s; \
                     registered tags: %s, requested tags: %s""",
                                                                 metricName,
                                                                 Arrays.asList(establishedTags),
                                                                 Arrays.asList(candidateTags)));
            }
        }
    }

    /**
     * Returns whether the tag names in the two provided metric IDs are consistent with each other.
     *
     * @param a one metric ID
     * @param b another metric ID
     * @return {@code true} if the tag name sets in the two IDs are consistent; {@code false} otherwise
     */
    static boolean isConsistentTagNameSet(MetricID a, MetricID b) {
        return a.getTags().keySet().equals(b.getTags().keySet());
    }

    /**
     * Checks to make sure the specified tags do not include any reserved tag names.
     *
     * @param tags tags to check
     * @throws java.lang.IllegalArgumentException if any of names of the specified tags are reserved
     */
    static void checkForReservedTags(Tag... tags) {
        if (tags != null) {
            Set<String> offendingReservedTags = null;
            for (Tag tag : tags) {
                if (RESERVED_TAG_NAMES.contains(tag.getTagName())) {
                    if (offendingReservedTags == null) {
                        offendingReservedTags = new HashSet<>();
                    }
                    offendingReservedTags.add(tag.getTagName());
                }
            }
            if (offendingReservedTags != null) {
                throw new IllegalArgumentException("Provided tags includes reserved name(s) " + offendingReservedTags);
            }
        }
    }

    /**
     * Checks to make sure the specified tags do not include any reserved tag names.
     *
     * @param tagNames tag names to check
     * @throws java.lang.IllegalArgumentException if any of names of the specified tags are reserved
     */
    static void checkForReservedTags(Set<String> tagNames) {
        if (tagNames != null) {
            Set<String> offendingReservedTags = null;
            for (String tagName : tagNames) {
                if (RESERVED_TAG_NAMES.contains(tagName)) {
                    if (offendingReservedTags == null) {
                        offendingReservedTags = new HashSet<>();
                    }
                    offendingReservedTags.add(tagName);
                }
            }
            if (offendingReservedTags != null) {
                throw new IllegalArgumentException("Provided tags includes reserved name(s) " + offendingReservedTags);
            }
        }
    }
}
