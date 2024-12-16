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

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Objects;

/**
 * Wait strategy.
 */
@SuppressWarnings("unused")
public sealed abstract class WaitStrategy {

    private static final System.Logger LOGGER = System.getLogger(WaitStrategy.class.getName());
    private static final String PORT_REGEX = ".* http://localhost:([0-9]*) ?.*";

    private long timeout = 60;

    private WaitStrategy() {
    }

    /**
     * Wait for the configured port to be open.
     *
     * @return WaitStrategy
     */
    public static WaitStrategy waitForPort() {
        return new ConfiguredPortWaitStrategy();
    }

    /**
     * Wait for the given port to be open.
     *
     * @return WaitStrategy
     */
    public static WaitStrategy waitForPort(int port) {
        return new PortWaitStrategy(port);
    }

    /**
     * Wait for the process to complete.
     *
     * @return WaitStrategy
     */
    public static WaitStrategy waitForCompletion() {
        return new CompletionWaitStrategy();
    }

    /**
     * Wait for the given regex to match in the standard output.
     *
     * @param regex regex
     * @return WaitStrategy
     */
    public static WaitStrategy waitForLine(String regex) {
        return new RegexWaitStrategy(regex);
    }

    @SuppressWarnings("BusyWait")
    void await(ProcessMonitor monitor, int port) {
        Process process = monitor.get();
        long pid = process.pid();
        String description = toString(monitor, port);
        LOGGER.log(Level.INFO, String.format("Waiting for %s, pid=%d", description, pid));
        long timeoutMillis = timeout * 1000;
        long startTime = System.currentTimeMillis();
        while (process.isAlive() && System.currentTimeMillis() - startTime < timeoutMillis) {
            if (test(monitor, port)) {
                LOGGER.log(Level.INFO, "Process ready, pid=" + pid);
                return;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (test(monitor, port)) {
            LOGGER.log(Level.INFO, "Process ready, pid=" + pid);
        } else if (!process.isAlive()) {
            throw new IllegalStateException("Process is not alive");
        } else {
            throw new RuntimeException(String.format("Timeout waiting for %s, pid=%d", description, pid));
        }
    }

    /**
     * Test the strategy.
     *
     * @param monitor        process monitor
     * @param configuredPort configured port
     * @return {@code true} if wait condition is sucessful
     */
    abstract boolean test(ProcessMonitor monitor, int configuredPort);

    /**
     * Get the strategy description.
     *
     * @param monitor        process monitor
     * @param configuredPort configured port
     * @return description
     */
    abstract String toString(ProcessMonitor monitor, int configuredPort);

    /**
     * Set the timeout.
     *
     * @param timeout timeout
     */
    public void timeout(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout is null").toSeconds();
    }

    /**
     * Get the timeout in seconds.
     *
     * @return timeout
     */
    public long timeout() {
        return timeout;
    }

    private static boolean isPortOpen(int port) {
        SocketAddress sa = new InetSocketAddress("localhost", port);
        try (Socket socket = new Socket()) {
            socket.connect(sa, 1000);
            LOGGER.log(Level.TRACE, "Socket localhost:%d is ready", port);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.TRACE, "Socket localhost:%d exception: %s", port, ex.getMessage());
            return false;
        }
    }

    private static boolean matchLines(String output, String regex) {
        return output.lines().anyMatch(line -> line.matches(regex));
    }

    private static final class PortWaitStrategy extends WaitStrategy {

        private final int port;

        PortWaitStrategy(int port) {
            this.port = port;
        }

        @Override
        public boolean test(ProcessMonitor monitor, int ignored) {
            return isPortOpen(port);
        }

        @Override
        String toString(ProcessMonitor monitor, int ignored) {
            return "port " + port;
        }
    }

    private static final class ConfiguredPortWaitStrategy extends WaitStrategy {

        @Override
        public boolean test(ProcessMonitor monitor, int port) {
            return port > 0 ? isPortOpen(port) : matchLines(monitor.output(), PORT_REGEX);
        }

        @Override
        String toString(ProcessMonitor monitor, int port) {
            return port > 0 ? "port " + port : "regex " + PORT_REGEX;
        }
    }

    private static final class CompletionWaitStrategy extends WaitStrategy {

        @Override
        public boolean test(ProcessMonitor monitor, int port) {
            return !monitor.get().isAlive();
        }

        @Override
        String toString(ProcessMonitor monitor, int port) {
            return "completion";
        }
    }

    private static final class RegexWaitStrategy extends WaitStrategy {

        private final String regex;

        private RegexWaitStrategy(String regex) {
            this.regex = regex;
        }

        @Override
        public boolean test(ProcessMonitor monitor, int port) {
            return matchLines(monitor.output(), regex);
        }

        @Override
        String toString(ProcessMonitor monitor, int port) {
            return "regex " + regex;
        }
    }
}
