/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.LazyValue;

/**
 * Process monitor.
 */
public final class ProcessMonitor implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(ProcessMonitor.class.getName());
    private static final LazyValue<ShutdownThread> SHUTDOWN = LazyValue.create(ShutdownThread::new);
    private static final String PORT_REGEX = ".* http://localhost:([0-9]*) ?.*";
    private static final Pattern PORT_PATTERN = Pattern.compile(PORT_REGEX);
    private static final String LINE_SEP = System.lineSeparator();

    private final Lock outputLock = new ReentrantLock();
    private final StringBuilder output = new StringBuilder();
    private final int port;
    private final Process process;
    private final WaitStrategy waitStrategy;

    ProcessMonitor(ProcessBuilder pb, int port, WaitStrategy waitStrategy) {
        try {
            this.port = port;
            this.waitStrategy = waitStrategy;
            LOGGER.log(System.Logger.Level.INFO, "Executing " + String.join(" ", pb.command()));
            Thread stdoutReader = new Thread(this::logStdout);
            Thread stderrReader = new Thread(this::logStderr);
            process = pb.start();
            SHUTDOWN.get().add(process);
            stdoutReader.start();
            stderrReader.start();
        } catch (Throwable ex) {
            throw new RuntimeException("Failed to start process", ex);
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Stop the process.
     */
    public void stop() {
        if (process != null && process.isAlive()) {
            LOGGER.log(System.Logger.Level.INFO, "Stopping process: " + process.pid());
            process.destroy();
            SHUTDOWN.get().remove(process);
        }
    }

    /**
     * Get the process.
     *
     * @return Process
     */
    public Process get() {
        return process;
    }

    /**
     * Get the process combined output.
     *
     * @return output
     */
    public String output() {
        return output.toString();
    }

    /**
     * Wait for the {@link WaitStrategy strategy}.
     *
     * @return this instance
     */
    public ProcessMonitor await() {
        return await(waitStrategy);
    }

    /**
     * Wait for the given {@link WaitStrategy strategy}.
     *
     * @param waitStrategy wait strategy.
     * @return this instance
     */
    public ProcessMonitor await(WaitStrategy waitStrategy) {
        waitStrategy.await(this, port);
        return this;
    }

    /**
     * Get the port.
     *
     * @return port
     */
    public int port() {
        return port > 0 ? port : resolvePort();
    }

    private int resolvePort() {
        return output().lines()
                .map(PORT_PATTERN::matcher)
                .filter(Matcher::matches)
                .findFirst()
                .map(m -> m.group(1))
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalStateException("Unable to find port"));
    }

    private void logStdout() {
        logStream(process.getInputStream(), System.out);
    }

    private void logStderr() {
        logStream(process.getErrorStream(), System.err);
    }

    private void logStream(InputStream is, PrintStream printStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        try {
            String line = reader.readLine();
            while (line != null) {
                printStream.println(line);
                printStream.flush();
                outputLock.lock();
                output.append(line).append(LINE_SEP);
                outputLock.unlock();
                line = reader.readLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class ShutdownThread extends Thread {

        private final List<Process> processes = Collections.synchronizedList(new ArrayList<>());

        ShutdownThread() {
            Runtime.getRuntime().addShutdownHook(this);
        }

        void add(Process process) {
            processes.add(process);
        }

        void remove(Process process) {
            processes.remove(process);
        }

        @Override
        public void run() {
            // use a copy to avoid concurrent modifications
            for (Process process : List.copyOf(processes)) {
                if (process.isAlive()) {
                    LOGGER.log(System.Logger.Level.INFO, "Stopping process: " + process.pid());
                    process.destroyForcibly();
                }
            }
        }
    }
}
