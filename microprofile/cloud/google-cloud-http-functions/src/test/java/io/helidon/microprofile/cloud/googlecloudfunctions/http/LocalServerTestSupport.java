/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.cloud.googlecloudfunctions.http;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.functions.invoker.runner.Invoker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class LocalServerTestSupport {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final String SERVER_READY_STRING = "Started ServerConnector";
    private static AtomicInteger nextPort = new AtomicInteger(8080);

    private LocalServerTestSupport() {
    }

    static ServerProcess startServer(Class<?> helidonFunction, String type) throws InterruptedException, IOException {

        // Get the Java class path.
        String myClassPath = System.getProperty("java.class.path");
        assertNotNull(myClassPath);

        // Setup the Java Process command line string
        List<String> command = Arrays.asList(getJavaCommand(), "-classpath", myClassPath, Invoker.class.getName());
        ProcessBuilder processBuilder = new ProcessBuilder().command(command).redirectErrorStream(true);

        // Set environment variables.
        Map<String, String> environment = new HashMap<>();
        environment.put("PORT", String.valueOf(nextPort.getAndIncrement()));
        environment.put("K_SERVICE", "test-function");
        environment.put("FUNCTION_SIGNATURE_TYPE", type);
        environment.put("FUNCTION_TARGET", helidonFunction.getName());
        processBuilder.environment().putAll(environment);

        // Start the process and monitor the output logs in a separate thread.
        // Once the SERVER_READY_STRING is found in the logs, we know we are ready.
        Process serverProcess = processBuilder.start();
        CountDownLatch ready = new CountDownLatch(1);

        EXECUTOR.submit(() -> monitorOutput(serverProcess.getInputStream(), ready));
        boolean serverReady = ready.await(5, TimeUnit.SECONDS);
        if (!serverReady) {
            serverProcess.destroy();
            throw new AssertionError("Server never became ready");
        }

        return new ServerProcess(serverProcess);
    }

    private static void monitorOutput(InputStream processOutput, CountDownLatch ready) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(processOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(SERVER_READY_STRING)) {
                    ready.countDown();
                }
                System.out.println(line);
                if (line.contains("WARNING")) {
                    throw new AssertionError("Found warning in server output:\n" + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the path to the java executable.
     */
    private static String getJavaCommand() {
        File javaHome = new File(System.getProperty("java.home"));
        assertTrue(javaHome.exists());
        File javaBin = new File(javaHome, "bin");
        File javaCommand = new File(javaBin, "java");
        assertTrue(javaCommand.exists());
        return javaCommand.toString();
    }

    static class ServerProcess implements AutoCloseable {
        private final Process process;

        ServerProcess(Process process) {
            this.process = process;
        }

        Process process() {
            return process;
        }

        @Override
        public void close() {
            process().destroy();
        }
    }
}
