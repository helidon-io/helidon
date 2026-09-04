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
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.runner.IterationType;

/**
 * Reports total process CPU consumption as the average number of effective cores used by a JMH iteration.
 * The final measurement is omitted because JMH includes trial teardown in that iteration before profiler collection.
 * Register this profiler last so reverse stop order excludes other profiler cleanup.
 */
public final class ProcessCpuProfiler implements InternalProfiler {
    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";

    private final OperatingSystemMXBean operatingSystem;
    private Recording recording;
    private Path recordingPath;
    private long cpuStarted;
    private long wallStarted;
    private String benchmarkId;
    private int measurementIteration;

    /**
     * Creates the profiler.
     */
    public ProcessCpuProfiler() {
        var systemBean = ManagementFactory.getOperatingSystemMXBean();
        if (!(systemBean instanceof OperatingSystemMXBean bean)) {
            throw new IllegalStateException("Process CPU time is not available from the platform MXBean");
        }
        operatingSystem = bean;
    }

    @Override
    public String getDescription() {
        return "Process CPU time normalized to effective cores";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        if (recording != null) {
            throw new IllegalStateException("Previous process-CPU recording is still active");
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
            candidatePath = Files.createTempFile("helidon-process-cpu-", ".jfr");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create process-CPU recording", e);
        }
        Recording candidate = null;
        long candidateCpuStarted;
        long candidateWallStarted;
        try {
            candidate = new Recording();
            candidate.enable(EXECUTION_SAMPLE)
                    .withPeriod(Duration.ofMillis(10))
                    .withStackTrace();
            candidate.start();
            candidateWallStarted = System.nanoTime();
            candidateCpuStarted = operatingSystem.getProcessCpuTime();
            if (candidateCpuStarted < 0) {
                throw new IllegalStateException("Process CPU time is unavailable");
            }
        } catch (RuntimeException | Error failure) {
            ProcessProfilerSupport.cleanup(candidate, candidatePath, failure);
            throw failure;
        }
        recording = candidate;
        recordingPath = candidatePath;
        cpuStarted = candidateCpuStarted;
        wallStarted = candidateWallStarted;
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                       IterationParams iterationParams,
                                                       IterationResult result) {
        long cpuFinished = operatingSystem.getProcessCpuTime();
        long wallNanos = System.nanoTime() - wallStarted;
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
            current.dump(currentPath);
            long[] componentSamples = new long[ProcessProfilerSupport.Component.values().length];
            long totalSamples = 0;
            try (RecordingFile recordingFile = new RecordingFile(currentPath)) {
                while (recordingFile.hasMoreEvents()) {
                    var event = recordingFile.readEvent();
                    if (!EXECUTION_SAMPLE.equals(event.getEventType().getName())) {
                        throw new IllegalStateException("Unexpected JFR event in process-CPU recording");
                    }
                    ProcessProfilerSupport.Component component = ProcessProfilerSupport.component(event);
                    componentSamples[component.ordinal()]++;
                    totalSamples++;
                }
            }

            List<Result> results = new ArrayList<>();
            long operations = result.getMetadata().getAllOps();
            if (wallNanos <= 0 || cpuFinished < cpuStarted || operations <= 0) {
                throw new IllegalStateException("Process CPU accounting returned an invalid measurement interval");
            }
            long cpuNanos = cpuFinished - cpuStarted;
            results.add(new OptionalScalarResult("process.cpu.cores",
                                                 (double) cpuNanos / wallNanos,
                                                 "cores",
                                                 AggregationPolicy.AVG));
            results.add(new OptionalScalarResult("process.cpu.time.norm",
                                                 (double) cpuNanos / operations,
                                                 "ns/op",
                                                 AggregationPolicy.AVG));
            if (totalSamples > 0) {
                for (ProcessProfilerSupport.Component component : ProcessProfilerSupport.Component.values()) {
                    results.add(new OptionalScalarResult("process.cpu.sample." + component.label(),
                                                         (double) componentSamples[component.ordinal()]
                                                                 * 100 / totalSamples,
                                                         "percent",
                                                         AggregationPolicy.AVG));
                }
            }
            return results;
        } catch (IOException e) {
            var failure = new UncheckedIOException("Could not read process-CPU recording", e);
            primaryFailure = failure;
            throw failure;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            ProcessProfilerSupport.cleanup(current, currentPath, primaryFailure);
        }
    }
}
