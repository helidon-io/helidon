/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.health.checks;

import java.util.stream.Stream;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

class MemoryHealthCheckTest {
    private static final long MAX_MEMORY = 10000L;
    private static final long TOTAL_MEMORY_START = 5000L;
    private static final long THRESHOLD_MEMORY = 9000L;
    private static final double THRESHOLD_PERCENT = 90;

    private Runtime runtime;

    private static Stream<Arguments> belowThresholdParams() {
        return Stream.of(
                Arguments.of(0L, TOTAL_MEMORY_START, "100.00%"),
                Arguments.of(3000L, TOTAL_MEMORY_START, "70.00%"),
                Arguments.of(8000L, MAX_MEMORY, "20.00%"),
                Arguments.of(THRESHOLD_MEMORY, MAX_MEMORY, "10.00%")
        );
    }

    private static Stream<Arguments> aboveThresholdParams() {
        return Stream.of(
                Arguments.of(THRESHOLD_MEMORY + 1, MAX_MEMORY, "9.99%"), // Just over the threshold
                Arguments.of(MAX_MEMORY - 1, MAX_MEMORY, "0.01%"), // Just less than the MAX
                Arguments.of(MAX_MEMORY, MAX_MEMORY, "0.00%"), // The MAX
                Arguments.of(MAX_MEMORY + 1, MAX_MEMORY, "-0.01%") // More than the MAX!!
        );
    }

    @BeforeEach
    void init() {
        runtime = Mockito.mock(Runtime.class);
        Mockito.when(runtime.freeMemory()).thenReturn(TOTAL_MEMORY_START);  // Current free memory
        Mockito.when(runtime.maxMemory()).thenReturn(MAX_MEMORY);           // Max VM space that can be allocated
        Mockito.when(runtime.totalMemory()).thenReturn(TOTAL_MEMORY_START); // Total VM space currently allocated
    }

    private void setMemoryUsage(long used, long total) {
        Mockito.when(runtime.freeMemory()).thenReturn(total - used);
        Mockito.when(runtime.totalMemory()).thenReturn(total);
    }

    @Test
    void testThatHealthCheckNameDoesNotChange() {
        HeapMemoryHealthCheck check = new HeapMemoryHealthCheck(runtime, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat(response.getName(), is("heapMemory")); // Just verify it never changes accidentally
    }

    @ParameterizedTest
    @MethodSource("belowThresholdParams")
    void belowThreshold(long used, long total, String text) {
        setMemoryUsage(used, total);
        HeapMemoryHealthCheck check = new HeapMemoryHealthCheck(runtime, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat(response.getState(), is(HealthCheckResponse.State.UP));
        assertThat(response.getData().isPresent(), is(true));
        // Another test will make sure DiskSpaceHealthCheck returns the right stuff, so skipping
        // the textual return values
        assertThat(response.getData().get(), hasEntry("freeBytes", total - used));
        assertThat(response.getData().get(), hasEntry("maxBytes", MAX_MEMORY));
        assertThat(response.getData().get(), hasEntry("percentFree", text));
        assertThat(response.getData().get(), hasEntry("totalBytes", total));
    }

    @ParameterizedTest
    @MethodSource("aboveThresholdParams")
    void aboveThreshold(long used, long total, String text) {
        setMemoryUsage(used, total);
        HeapMemoryHealthCheck check = new HeapMemoryHealthCheck(runtime, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat(response.getState(), is(HealthCheckResponse.State.DOWN));
        assertThat(response.getData().isPresent(), is(true));
        // Another test will make sure DiskSpaceHealthCheck returns the right stuff, so skipping
        // the textual return values
        assertThat(response.getData().get(), hasEntry("freeBytes", total - used));
        assertThat(response.getData().get(), hasEntry("maxBytes", MAX_MEMORY));
        assertThat(response.getData().get(), hasEntry("percentFree", text));
        assertThat(response.getData().get(), hasEntry("totalBytes", total));
    }
}
