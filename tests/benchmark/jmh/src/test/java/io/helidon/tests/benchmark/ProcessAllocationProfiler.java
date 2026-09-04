/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.runner.IterationType;

/**
 * Reports process-wide observed TLAB refill capacity, outside-TLAB allocation, and weighted allocation samples without
 * depending on live-thread snapshots.
 * The final measurement is omitted because JMH includes trial teardown in that iteration before profiler collection.
 * Register this profiler after result-scanning profilers so JFR stops before their scans.
 */
public final class ProcessAllocationProfiler implements InternalProfiler {
    private static final String NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    private static final String OUTSIDE_TLAB = "jdk.ObjectAllocationOutsideTLAB";
    private static final String ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";

    private Recording recording;
    private Path recordingPath;
    private String benchmarkId;
    private int measurementIteration;

    @Override
    public String getDescription() {
        return "Process-wide JFR TLAB refill capacity, outside-TLAB allocation, and weighted allocation samples";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        if (recording != null) {
            throw new IllegalStateException("Previous process-allocation recording is still active");
        }
        if (!benchmarkParams.id().equals(benchmarkId)) {
            benchmarkId = benchmarkParams.id();
            measurementIteration = 0;
        }
        if (iterationParams.getType() == IterationType.MEASUREMENT) {
            measurementIteration++;
        }
        Path candidatePath;
        try {
            candidatePath = Files.createTempFile("helidon-process-allocation-", ".jfr");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create process-allocation recording", e);
        }
        Recording candidate = null;
        try {
            candidate = new Recording();
            candidate.enable(NEW_TLAB).withoutStackTrace();
            candidate.enable(OUTSIDE_TLAB).withoutStackTrace();
            candidate.enable(ALLOCATION_SAMPLE).withStackTrace();
            candidate.start();
        } catch (RuntimeException | Error failure) {
            ProcessProfilerSupport.cleanup(candidate, candidatePath, failure);
            throw failure;
        }
        recording = candidate;
        recordingPath = candidatePath;
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        Recording current = recording;
        Path currentPath = recordingPath;
        recording = null;
        recordingPath = null;
        Throwable primaryFailure = null;
        try {
            current.stop();
            if (iterationParams.getType() == IterationType.MEASUREMENT
                    && measurementIteration == iterationParams.getCount()) {
                return List.of();
            }
            long wallNanos = Duration.between(current.getStartTime(), current.getStopTime()).toNanos();
            current.dump(currentPath);
            long tlabBytes = 0;
            long outsideTlabBytes = 0;
            long sampledBytes = 0;
            long tlabRefills = 0;
            long outsideTlabAllocations = 0;
            long[] componentSampledBytes = new long[ProcessProfilerSupport.Component.values().length];
            try (RecordingFile recordingFile = new RecordingFile(currentPath)) {
                while (recordingFile.hasMoreEvents()) {
                    RecordedEvent event = recordingFile.readEvent();
                    switch (event.getEventType().getName()) {
                    case NEW_TLAB -> {
                        tlabBytes = Math.addExact(tlabBytes, event.getLong("tlabSize"));
                        tlabRefills++;
                    }
                    case OUTSIDE_TLAB -> {
                        outsideTlabBytes = Math.addExact(outsideTlabBytes, event.getLong("allocationSize"));
                        outsideTlabAllocations++;
                    }
                    case ALLOCATION_SAMPLE -> {
                        long weight = event.getLong("weight");
                        sampledBytes = Math.addExact(sampledBytes, weight);
                        ProcessProfilerSupport.Component component = ProcessProfilerSupport.component(event);
                        componentSampledBytes[component.ordinal()] =
                                Math.addExact(componentSampledBytes[component.ordinal()], weight);
                    }
                    default -> throw new IllegalStateException("Unexpected JFR event in allocation recording");
                    }
                }
            }
            long capacityBytes = Math.addExact(tlabBytes, outsideTlabBytes);
            long operations = result.getMetadata().getAllOps();
            if (wallNanos <= 0 || operations <= 0) {
                throw new IllegalStateException("Process-allocation evidence has an invalid interval or operation count");
            }
            List<Result> results = new ArrayList<>();
            results.add(new OptionalScalarResult("process.alloc.capacity.rate",
                                                 mebibytesPerSecond(capacityBytes, wallNanos),
                                                 "MiB/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.capacity.rate.norm",
                                                 (double) capacityBytes / operations,
                                                 "B/op",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.tlab.capacity.rate",
                                                 mebibytesPerSecond(tlabBytes, wallNanos),
                                                 "MiB/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.tlab.capacity.rate.norm",
                                                 (double) tlabBytes / operations,
                                                 "B/op",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.outsideTlab.rate",
                                                 mebibytesPerSecond(outsideTlabBytes, wallNanos),
                                                 "MiB/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.outsideTlab.rate.norm",
                                                 (double) outsideTlabBytes / operations,
                                                 "B/op",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.sample.rate",
                                                 mebibytesPerSecond(sampledBytes, wallNanos),
                                                 "MiB/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.sample.rate.norm",
                                                 (double) sampledBytes / operations,
                                                 "B/op",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.tlab.refills.rate",
                                                 eventsPerSecond(tlabRefills, wallNanos),
                                                 "events/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.tlab.refills.norm",
                                                 (double) tlabRefills / operations,
                                                 "events/op",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.outsideTlab.events.rate",
                                                 eventsPerSecond(outsideTlabAllocations, wallNanos),
                                                 "events/sec",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.alloc.outsideTlab.events.norm",
                                                 (double) outsideTlabAllocations / operations,
                                                 "events/op",
                                                 AggregationPolicy.AVG));
            for (ProcessProfilerSupport.Component component : ProcessProfilerSupport.Component.values()) {
                results.add(new OptionalScalarResult("process.alloc.sample." + component.label() + ".rate.norm",
                                                     (double) componentSampledBytes[component.ordinal()] / operations,
                                                     "B/op",
                                                     AggregationPolicy.AVG));
            }
            return results;
        } catch (IOException e) {
            var failure = new UncheckedIOException("Could not read process-allocation recording", e);
            primaryFailure = failure;
            throw failure;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            ProcessProfilerSupport.cleanup(current, currentPath, primaryFailure);
        }
    }

    private static double mebibytesPerSecond(long bytes, long durationNanos) {
        return (double) bytes * TimeUnit.SECONDS.toNanos(1) / durationNanos / (1024 * 1024);
    }

    private static double eventsPerSecond(long events, long durationNanos) {
        return (double) events * TimeUnit.SECONDS.toNanos(1) / durationNanos;
    }
}
