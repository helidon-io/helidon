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

package io.helidon.inject.processor;

import java.util.List;

import javax.lang.model.element.TypeElement;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.mock;

class ProcessingTrackerTest {

    @Test
    void noDelta() {
        List<String> typeNames = List.of("a", "b", "c");
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> mock(TypeElement.class));
        assertThat(tracker.removedTypeNames().size(),
                   is(0));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "b", "c"));
    }

    @Test
    void incrementalCompilation() {
        List<String> typeNames = List.of("a", "b", "c");
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> mock(TypeElement.class));
        tracker.processing("b");

        assertThat(tracker.removedTypeNames().size(),
                   is(0));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "b", "c"));
    }

    @Test
    void incrementalCompilationWithFilesRemoved() {
        List<String> typeNames = List.of("a", "b", "c");
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> (typeName.equals("b") ? null : mock(TypeElement.class)));

        assertThat(tracker.removedTypeNames().size(),
                   is(1));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "c"));
    }

    @Test
    void incrementalCompilationWithFilesAddedAndRemoved() {
        List<String> typeNames = List.of("a");
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> mock(TypeElement.class));
        tracker.processing("b");
        tracker.processing("a");

        assertThat(tracker.removedTypeNames().size(),
                   is(0));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "b"));
    }

    @Test
    void cleanCompilation() {
        List<String> typeNames = List.of();
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> mock(TypeElement.class));
        tracker.processing("a");
        tracker.processing("b");
        tracker.processing("c");

        assertThat(tracker.removedTypeNames().size(),
                   is(0));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "b", "c"));
    }

    @Test
    void fullCompilationWithFilesAdded() {
        List<String> typeNames = List.of("a");
        ProcessingTracker tracker = new ProcessingTracker(null, typeNames,
                                                          typeName -> mock(TypeElement.class));
        tracker.processing("a");
        tracker.processing("b");
        tracker.processing("c");

        assertThat(tracker.removedTypeNames().size(),
                   is(0));
        assertThat(tracker.remainingTypeNames(),
                   containsInAnyOrder("a", "b", "c"));
    }

}
