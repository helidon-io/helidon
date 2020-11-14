/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.tests.integration.nativeimage.se1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HelidonProcessRunner {
    private static final Logger LOGGER = Logger.getLogger(HelidonProcessRunner.class.getName());

    private final ExecType execType;
    private final Runnable startCommand;
    private final Runnable stopCommand;
    private final int port;
    private AtomicReference<Process> process;

    private HelidonProcessRunner(AtomicReference<Process> process,
                                 ExecType execType,
                                 Runnable startCommand,
                                 Runnable stopCommand,
                                 int port) {
        this.process = process;
        this.execType = execType;
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
        this.port = port;
    }

    public void startApplication() {
        startCommand.run();
    }

    public ExecType execType() {
        return execType;
    }

    public void stopApplication() {
        stopCommand.run();
        if (process.get() != null) {
            process.get().destroy();
        }
    }

    public static HelidonProcessRunner create(ExecType execType,
                                              String mainClassModuleName,
                                              Class<?> mainClass,
                                              String finalName,
                                              Runnable inMemoryStartCommand,
                                              Runnable inMemoryStopCommand) {

        // ensure we run on a random port
        int port = findFreePort();
        if (execType == ExecType.IN_MEMORY) {
            System.setProperty("server.port", String.valueOf(port));
            return new HelidonProcessRunner(new AtomicReference<>(), execType, inMemoryStartCommand, inMemoryStopCommand, port);
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.environment().put("SERVER_PORT", String.valueOf(port));

        switch (execType) {
        case CLASS_PATH:
            addClasspathCommand(finalName, processBuilder);
            break;
        case MODULE_PATH:
            addModulePathCommand(finalName, mainClassModuleName, mainClass, processBuilder);
            break;
        case NATIVE:
            addNativeCommand(finalName, processBuilder);
            break;
        default:
            throw new IllegalArgumentException("Unsupported exec type: " + execType);
        }

        AtomicReference<Process> process = new AtomicReference<>();
        Runnable startCommand = () -> {
            try {
                // configure redirects
                Thread stdoutReader = new Thread(() -> logStdout(process));
                Thread stderrReader = new Thread(() -> logStderr(process));

                process.set(processBuilder.start());

                stdoutReader.start();
                stderrReader.start();

                waitForSocketOpen(process.get(), port);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to start process", e);
            }
        };

        Runnable stopCommand = () -> {
            Process p = process.get();
            if (p == null || !p.isAlive()) {
                return;
            }
            p.destroy();
        };

        return new HelidonProcessRunner(process, execType, startCommand, stopCommand, port);
    }

    private static void waitForSocketOpen(Process process, int port) {
        long timeoutSeconds = 20;
        long timeout = TimeUnit.SECONDS.toMillis(timeoutSeconds);

        long begin = System.currentTimeMillis();
        while (true) {
            // check port open
            if (portOpen(port)) {
                return;
            }
            // make sure process still alive
            if (!process.isAlive()) {
                throw new RuntimeException("Process failed to start. Exit code: " + process.exitValue());
            }
            // sleep
            try {
                TimeUnit.MILLISECONDS.sleep(150);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                throw new RuntimeException("Cancelling process startup, thread interrupted", e);
            }
            // timeout
            long now = System.currentTimeMillis();
            if (now - begin > timeout) {
                process.destroyForcibly();
                throw new RuntimeException("Process failed to start, time out after " + timeoutSeconds + " seconds");
            }
        }
    }

    private static boolean portOpen(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int findFreePort() {
        int port = 7001;

        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            port = socket.getLocalPort();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not find available port, using 7001", e);
        }

        return port;
    }

    /**
     * Log the process standard output.
     */
    private static void logStdout(AtomicReference<Process> process) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.get().getInputStream()));
        String line;
        try {
            line = reader.readLine();
            while (line != null) {
                System.out.println("* " + line);
                line = reader.readLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Log the process standard error.
     */
    private static void logStderr(AtomicReference<Process> process) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.get().getErrorStream()));
        String line;
        try {
            line = reader.readLine();
            while (line != null) {
                System.err.println("* " + line);
                line = reader.readLine();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void addModulePathCommand(String finalName,
                                             String moduleName,
                                             Class<?> mainClass,
                                             ProcessBuilder processBuilder) {
        String[] command = new String[] {
                "java",
                "--module-path",
                "target/" + finalName + ".jar:target/libs",
                "--module",
                moduleName + "/" + mainClass.getName()
        };

        LOGGER.info("Executing command: " + Arrays.toString(command));
        processBuilder.command(command);
    }

    private static void addClasspathCommand(String finalName, ProcessBuilder processBuilder) {
        processBuilder.command("java", "-jar", "target/" + finalName + ".jar");
    }

    private static void addNativeCommand(String finalName, ProcessBuilder processBuilder) {
        processBuilder.command("target/" + finalName);
    }

    public int port() {
        return port;
    }

    public enum ExecType {
        CLASS_PATH("classpath"),
        MODULE_PATH("module"),
        NATIVE("native"),
        IN_MEMORY("memory");

        private final String name;

        ExecType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
