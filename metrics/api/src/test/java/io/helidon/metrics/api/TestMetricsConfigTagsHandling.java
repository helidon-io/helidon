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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMetricsConfigTagsHandling {

    @Test
    void checkSingle() {
        var pairs = MetricsConfigBlueprint.createGlobalTags("a=4");
        assertThat("Result", pairs, hasSize(1));
        assertThat("Tag", pairs, hasItem(Tag.create("a", "4")));
    }

    @Test
    void checkMultiple() {
        var pairs = MetricsConfigBlueprint.createGlobalTags("a=11,b=12,c=13");
        assertThat("Result", pairs, hasSize(3));
        assertThat("Tags", pairs, allOf(hasItem(Tag.create("a", "11")),
                                        hasItem(Tag.create("b", "12")),
                                        hasItem(Tag.create("c", "13"))));
    }

    @Test
    void checkQuoted() {
        var pairs = MetricsConfigBlueprint.createGlobalTags("d=r\\=3,e=4,f=0\\,1,g=hi");
        assertThat("Result", pairs, hasSize(4));
        assertThat("Tags", pairs, allOf(hasItem(Tag.create("d", "r=3")),
                                        hasItem(Tag.create("e", "4")),
                                        hasItem(Tag.create("f", "0,1")),
                                        hasItem(Tag.create("g", "hi"))));
    }

    @Test
    void checkEmptyAssignment() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                MetricsConfigBlueprint.createGlobalTags(",a=1"));
        assertThat("Empty assignment", ex.getMessage(), containsString("empty"));
    }

    @Test
    void checkNoRightSide() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                MetricsConfigBlueprint.createGlobalTags("a="));
        assertThat("No right side", ex.getMessage(), containsString("missing tag value"));
    }

    @Test
    void checkNoLeftSide() {

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                MetricsConfigBlueprint.createGlobalTags("=1"));
        assertThat("No left side", ex.getMessage(), containsString("missing tag name"));
    }

    @Test
    void checkInvalidTagName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                MetricsConfigBlueprint.createGlobalTags("a*=1,"));
        assertThat("Invalid tag name", ex.getMessage(), containsString("tag name must"));
    }
}
