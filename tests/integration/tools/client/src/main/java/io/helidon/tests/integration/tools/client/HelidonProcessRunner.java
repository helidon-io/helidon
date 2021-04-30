/*
 * Copyright (c)  2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.tools.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.util.internal.SocketUtils;

/**
 * Process runner to start Helidon application.
 * Handles external process life cycle with various execution types.
 */
public class HelidonProcessRunner {

    private static final Logger LOGGER = Logger.getLogger(HelidonProcessRunner.class.getName());

    /** HTTP port of running application. Value is set to {@code -1} before application will be started. */
    public static int HTTP_PORT = -1;

    private final AtomicReference<Process> process;
    private final ExecType execType;
    private final Runnable startCommand;
    private final Runnable stopCommand;
    private final int port;

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

    /**
     * Start the application.
     * Value of {@code HTTP_PORT} is set to HTTP port of this application.
     */
    public void startApplication() {
        startCommand.run();
        HTTP_PORT = port;
    }

    /**
     * Return type of execution for the application.
     *
     * @return type of execution for the application
     */
    public ExecType execType() {
        return execType;
    }

    /**
     * Stop the application.
     * Value of {@code HTTP_PORT} is set to {@code -1}.
     */
    public void stopApplication() {
        stopCommand.run();
        if (process.get() != null) {
            process.get().destroy();
        }
        HTTP_PORT = -1;
    }

    /**
     * Create process runner with full configuration of modules.
     *
     * @param execType type of execution
     * @param moduleName name of the application module (JPMS), used for {@link HelidonProcessRunner.ExecType#MODULE_PATH}
     * @param mainClassModuleName name of the module of the main class (JPMS) - if the same, you can use
     *          {@link #create(HelidonProcessRunner.ExecType, String, String, String, Runnable, Runnable)},
     *                            used for {@link HelidonProcessRunner.ExecType#MODULE_PATH}
     * @param mainClass name of the main class to run - if this is MP and you want to use the default
     *                  {@code io.helidon.microprofile.cdi.Main}, you can use
     *                  {@link #createMp(HelidonProcessRunner.ExecType, String, String, Runnable, Runnable)},
     *                  used for {@link HelidonProcessRunner.ExecType#MODULE_PATH}
     * @param finalName final name of the artifact - this is the expected name of the native image and jar file
     * @param args arguments passed to main method
     * @param inMemoryStartCommand command to start the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     * @param inMemoryStopCommand command to stop the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     *
     * @return a process runner that will start Helidon with the expected exec type when {@link #startApplication()} is called
     */
    public static HelidonProcessRunner create(ExecType execType,
                                              String moduleName,
                                              String mainClassModuleName,
                                              String mainClass,
                                              String finalName,
                                              String[] args,
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
            addClasspathCommand(finalName, args, processBuilder);
            break;
        case MODULE_PATH:
            addModulePathCommand(finalName, moduleName, mainClassModuleName, mainClass, args, processBuilder);
            break;
        case NATIVE:
            addNativeCommand(finalName, args, processBuilder);
            break;
        case JLINK_CLASS_PATH:
            addJlinkCommand(finalName, args, processBuilder);
            break;
        case JLINK_MODULE_PATH:
            addJlinkModuleCommand(finalName, moduleName, mainClassModuleName, mainClass, args, processBuilder);
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

    /**
     * Create process runner with MicroProfile specific configuration of modules.
     *
     * @param execType type of execution
     * @param moduleName name of the application module (JPMS), used for {@link HelidonProcessRunner.ExecType#MODULE_PATH}
     * @param finalName final name of the artifact - this is the expected name of the native image and jar file
     * @param args arguments passed to main method
     * @param inMemoryStartCommand command to start the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     * @param inMemoryStopCommand command to stop the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     *
     * @return a process runner that will start Helidon with the expected exec type when {@link #startApplication()} is called
     */
    public static HelidonProcessRunner createMp(ExecType execType,
                                                String moduleName,
                                                String finalName,
                                                String[] args,
                                                Runnable inMemoryStartCommand,
                                                Runnable inMemoryStopCommand) {
        return create(execType,
                      moduleName,
                      "io.helidon.microprofile.cdi",
                      "io.helidon.microprofile.cdi.Main",
                      finalName,
                      args,
                      inMemoryStartCommand,
                      inMemoryStopCommand);
    }

    /**
     * Create process runner with main class in the application module (JPMS).
     * Both the application module and the module of the main class share the same name.
     *
     * @param execType type of execution
     * @param mainClassModuleName name of the application module with the main class (JPMS)
     * @param mainClass name of the main class to run - if this is MP and you want to use the default
     *                  {@code io.helidon.microprofile.cdi.Main}, you can use
     *                  {@link #createMp(HelidonProcessRunner.ExecType, String, String, Runnable, Runnable)},
     *                  used for {@link HelidonProcessRunner.ExecType#MODULE_PATH}
     * @param finalName final name of the artifact - this is the expected name of the native image and jar file
     * @param args arguments passed to main method
     * @param inMemoryStartCommand command to start the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     * @param inMemoryStopCommand command to stop the server in memory, used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}
     *
     * @return a process runner that will start Helidon with the expected exec type when {@link #startApplication()} is called
     */
    public static HelidonProcessRunner create(ExecType execType,
                                              String mainClassModuleName,
                                              String mainClass,
                                              String finalName,
                                              String[] args,
                                              Runnable inMemoryStartCommand,
                                              Runnable inMemoryStopCommand) {

        return create(execType,
                      mainClassModuleName,
                      mainClassModuleName,
                      mainClass,
                      finalName,
                      args,
                      inMemoryStartCommand,
                      inMemoryStopCommand);
    }

    private static void waitForSocketOpen(Process process, int port) {
        long timeoutSeconds = 20;
        long timeout = TimeUnit.SECONDS.toMillis(timeoutSeconds);

        long begin = System.currentTimeMillis();
        LOGGER.finest(() -> String.format("Waiting for %d to listen", port));
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
        SocketAddress sa = SocketUtils.socketAddress("localhost", port);
        try {
            Socket socket = new Socket();
            socket.connect(sa, 1000);
            LOGGER.finest(() -> String.format("Socket localhost:%d is ready", port));
            return true;
        } catch (IOException ex) {
            LOGGER.finest(() -> String.format("Socket localhost:%d exception: %s", port, ex.getMessage()));
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
                                             String mainClassModuleName,
                                             String mainClass,
                                             String[] args,
                                             ProcessBuilder processBuilder) {
        addModuleCommand("java",
                         "target",
                         finalName,
                         moduleName,
                         mainClassModuleName,
                         mainClass,
                         args,
                         processBuilder);
    }

    private static void addJlinkModuleCommand(String finalName,
                                              String moduleName,
                                              String mainClassModuleName,
                                              String mainClass,
                                              String[] args,
                                              ProcessBuilder processBuilder) {
        String jriDir = "target/" + finalName + "-jri";

        // TODO
        // this should work - instead of java, we should use the jriDir + "/bin/start" command
        // with a switch such as "--modules"
        addModuleCommand(jriDir + "/bin/java",
                         jriDir + "/app",
                         finalName,
                         moduleName,
                         mainClassModuleName,
                         mainClass,
                         args,
                         processBuilder);
    }

    private static void addModuleCommand(String java,
                                         String location,
                                         String finalName,
                                         String moduleName,
                                         String mainClassModuleName,
                                         String mainClass,
                                         String[] args,
                                         ProcessBuilder processBuilder) {
        List<String> command = new ArrayList<>(6);
        command.add(java);
        command.add("--module-path");
        command.add(location + "/" + finalName + ".jar" + File.pathSeparator + "target/libs");
        command.add("--module");
        command.add(mainClassModuleName + "/" + mainClass);

        if (!Objects.equals(moduleName, mainClassModuleName)) {
            // we are running main class from another module, need to add current module
            command.add("--add-modules");
            command.add(moduleName);
        }

        LOGGER.fine(() -> String.format("Command: %s", command));
        processBuilder.command(buildCommand(args, command));
    }

    private static void addJlinkCommand(String finalName, String[] args, ProcessBuilder processBuilder) {
        String jriDir = "target/" + finalName + "-jri";

        // TODO
        // this should work - instead of java -jar, we should use the jriDir + "/bin/start" command
        processBuilder.command(
                buildCommand(args,
                        jriDir + "/bin/java",
                        "-jar",
                        jriDir + "/app/" + finalName + ".jar"));
    }

    private static void addClasspathCommand(String finalName, String[] args, ProcessBuilder processBuilder) {
        processBuilder.command(
                buildCommand(args, "java", "-jar", "target/" + finalName + ".jar"));
    }

    private static void addNativeCommand(String finalName, String[] args, ProcessBuilder processBuilder) {

        processBuilder.command(
                buildCommand(args, "target/" + finalName));
    }

    private static List<String> buildCommand(final String[] args, final String ...command) {
        return buildCommand(args, Arrays.asList(command));
    }

    private static List<String> buildCommand(final String[] args, final List<String> command) {
        final List<String> commandList = new LinkedList<>(command);
        if (args != null) {
            commandList.addAll(Arrays.asList(args));
        }
        return commandList;
    }

    /**
     * Return HTTP port of the application.
     *
     * @return HTTP port of the application
     */
    public int port() {
        return port;
    }

    /**
     * Application execution types.
     */
    public enum ExecType {
        CLASS_PATH("classpath"),
        MODULE_PATH("module"),
        NATIVE("native"),
        IN_MEMORY("memory"),
        JLINK_CLASS_PATH("jlink-cp"),
        JLINK_MODULE_PATH("jlink-module");

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
