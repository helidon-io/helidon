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

import java.io.IOException;
import java.nio.file.FileStore;
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

class DiskSpaceHealthCheckTest {
    private static final long TOTAL_DISK = 10000L;
    private static final long USED_DISK_START = 5000L;
    private static final long THRESHOLD_DISK = 9000L;
    private static final double THRESHOLD_PERCENT = 90;

    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static final long GB = 1024 * MB;
    private static final long TB = 1024 * GB;
    private static final long PB = 1024 * TB;

    private FileStore fileStore;

    private static Stream<Arguments> belowThresholdParams() {
        return Stream.of(
                Arguments.of(0L, "100.00%"),
                Arguments.of(3000L, "70.00%"),
                Arguments.of(8000L, "20.00%"),
                Arguments.of(THRESHOLD_DISK, "10.00%")
        );
    }

    private static Stream<Arguments> aboveThresholdParams() {
        return Stream.of(
                Arguments.of(THRESHOLD_DISK + 1, "9.99%"), // Just over the threshold
                Arguments.of(TOTAL_DISK - 1, "0.01%"), // Just less than the total
                Arguments.of(TOTAL_DISK, "0.00%"), // The total
                Arguments.of(TOTAL_DISK + 1, "-0.01%") // More than the total!!
        );
    }

    private static Stream<Arguments> bytesToUnitsParams() {
        return Stream.of(
                Arguments.of(0L, "0 bytes"),
                Arguments.of(KB - 1, "1023 bytes"),
                Arguments.of(KB, "1.00 KB"),
                Arguments.of(3137L, "3.06 KB"),
                Arguments.of(MB - 10, "1023.99 KB"),
                Arguments.of(MB, "1.00 MB"),
                Arguments.of(GB - 10000, "1023.99 MB"),
                Arguments.of(GB, "1.00 GB"),
                Arguments.of(TB - 10000000, "1023.99 GB"),
                Arguments.of(TB, "1.00 TB"),
                Arguments.of(PB - 10000000000L, "1023.99 TB"),
                Arguments.of(PB, "1.00 PB")
        );
    }

    @BeforeEach
    void init() throws IOException {
        fileStore = Mockito.mock(FileStore.class);
        Mockito.when(fileStore.getUsableSpace()).thenReturn(TOTAL_DISK - USED_DISK_START);
        Mockito.when(fileStore.getTotalSpace()).thenReturn(TOTAL_DISK);
    }

    private void setDiskUsage(long used) throws IOException {
        Mockito.when(fileStore.getUsableSpace()).thenReturn(TOTAL_DISK - used);
    }

    @Test
    void testThatHealthCheckNameDoesNotChange() {
        DiskSpaceHealthCheck check = new DiskSpaceHealthCheck(fileStore, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat("diskSpace", is(response.getName()));
    }

    @ParameterizedTest
    @MethodSource("belowThresholdParams")
    void belowThreshold(long used, String text) throws IOException {
        setDiskUsage(used);
        DiskSpaceHealthCheck check = new DiskSpaceHealthCheck(fileStore, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat(HealthCheckResponse.State.UP, is(response.getState()));
        assertThat(response.getData().isPresent(), is(true));
        // Another test will make sure DiskSpaceHealthCheck returns the right stuff, so skipping
        // the textual return values
        assertThat(response.getData().get(), hasEntry("freeBytes", TOTAL_DISK - used));
        assertThat(response.getData().get(), hasEntry("percentFree", text));
        assertThat(response.getData().get(), hasEntry("totalBytes", TOTAL_DISK));
    }

    @ParameterizedTest
    @MethodSource("aboveThresholdParams")
    void aboveThreshold(long used, String text) throws IOException {
        setDiskUsage(used);
        DiskSpaceHealthCheck check = new DiskSpaceHealthCheck(fileStore, THRESHOLD_PERCENT);
        HealthCheckResponse response = check.call();
        assertThat(HealthCheckResponse.State.DOWN, is(response.getState()));
        assertThat(response.getData().isPresent(), is(true));
        // Another test will make sure DiskSpaceHealthCheck returns the right stuff, so skipping
        // the textual return values
        assertThat(response.getData().get(), hasEntry("freeBytes", TOTAL_DISK - used));
        assertThat(response.getData().get(), hasEntry("percentFree", text));
        assertThat(response.getData().get(), hasEntry("totalBytes", TOTAL_DISK));
    }

    @ParameterizedTest
    @MethodSource("bytesToUnitsParams")
    void testBytesToUnits(long bytes, String text) {
        assertThat(text, is(DiskSpaceHealthCheck.format(bytes)));
    }
}
