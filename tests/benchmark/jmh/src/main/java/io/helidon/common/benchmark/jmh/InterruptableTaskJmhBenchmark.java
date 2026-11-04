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

package io.helidon.common.benchmark.jmh;

import java.util.concurrent.TimeUnit;

import io.helidon.common.task.InterruptableTask;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class InterruptableTaskJmhBenchmark {
    private final RunTask runTask = new RunTask();
    private final CallTask callTask = new CallTask();

    @Setup
    public void setup() throws Exception {
        // Exercise both adapters before measuring steady-state execution.
        runTask.call();
        callTask.run();
    }

    @Benchmark
    public int runOnlyCall() throws Exception {
        runTask.call();
        return runTask.executions;
    }

    @Benchmark
    public int callOnlyRun() {
        callTask.run();
        return callTask.executions;
    }

    private static class RunTask implements InterruptableTask<Void> {
        private int executions;

        @Override
        public boolean canInterrupt() {
            return true;
        }

        @Override
        public void run() {
            executions++;
        }
    }

    private static class CallTask implements InterruptableTask<Integer> {
        private int executions;

        @Override
        public boolean canInterrupt() {
            return true;
        }

        @Override
        public Integer call() {
            return ++executions;
        }
    }
}
