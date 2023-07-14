/*
 * Copyright (c)  2021, 2023 Oracle and/or its affiliates.
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Process runner to start Helidon application.
 * Handles external process life cycle with various execution types.
 */
public class HelidonProcessRunner {

    private static final System.Logger LOGGER = System.getLogger(HelidonProcessRunner.class.getName());

    /**
     * HTTP port of running application. Value is set to {@code -1} before application will be started.
     */
    public static int HTTP_PORT = -1;

    private final AtomicReference<Process> process = new AtomicReference<>();
    private final ExecType execType;
    private final Runnable startCommand;
    private final Runnable stopCommand;
    private final int port;

    private HelidonProcessRunner(Builder builder) {
        this.execType = builder.execType;
        this.port = findFreePort();
        if (execType == ExecType.IN_MEMORY) {
            this.startCommand = builder.inMemoryStartCommand;
            this.stopCommand = builder.inMemoryStopCommand;
            System.setProperty("server.port", String.valueOf(port));
        } else {
            ProcessBuilder pb = new ProcessBuilder();
            pb.environment().put("SERVER_PORT", String.valueOf(port));
            switch (execType) {
                case CLASS_PATH -> addClasspathCommand(builder, pb);
                case MODULE_PATH -> addModulePathCommand(builder, pb);
                case NATIVE -> addNativeCommand(builder, pb);
                case JLINK_CLASS_PATH -> addJlinkCommand(builder, pb);
                case JLINK_MODULE_PATH -> addJlinkModuleCommand(builder, pb);
                default -> throw new IllegalArgumentException("Unsupported exec type: " + execType);
            }
            startCommand = () -> {
                try {
                    // configure redirects
                    Thread stdoutReader = new Thread(() -> logStdout(process));
                    Thread stderrReader = new Thread(() -> logStderr(process));

                    process.set(pb.start());

                    stdoutReader.start();
                    stderrReader.start();

                    waitForSocketOpen(process.get(), port);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to start process", e);
                }
            };
            stopCommand = () -> {
                Process p = process.get();
                if (p == null || !p.isAlive()) {
                    return;
                }
                p.destroy();
            };
        }
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the application.
     * Value of {@code HTTP_PORT} is set to HTTP port of this application.
     *
     * @return this instance
     */
    public HelidonProcessRunner startApplication() {
        startCommand.run();
        HTTP_PORT = port;
        return this;
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
     *
     * @return this instance
     */
    public HelidonProcessRunner stopApplication() {
        stopCommand.run();
        if (process.get() != null) {
            process.get().destroy();
        }
        HTTP_PORT = -1;
        return this;
    }

    private static void waitForSocketOpen(Process process, int port) {
        long timeoutSeconds = 20;
        long timeout = TimeUnit.SECONDS.toMillis(timeoutSeconds);

        long begin = System.currentTimeMillis();
        LOGGER.log(Level.TRACE, () -> String.format("Waiting for %d to listen", port));
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
        SocketAddress sa = new InetSocketAddress("localhost", port);
        try (Socket socket = new Socket()) {
            socket.connect(sa, 1000);
            LOGGER.log(Level.TRACE, () -> String.format("Socket localhost:%d is ready", port));
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.TRACE, () -> String.format("Socket localhost:%d exception: %s", port, ex.getMessage()));
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

    private static void addModulePathCommand(Builder builder, ProcessBuilder processBuilder) {
        addModuleCommand("java",
                "target",
                builder.finalName,
                builder.moduleName,
                builder.mainClassModuleName,
                builder.mainClass,
                builder.args,
                processBuilder);
    }

    private static void addJlinkModuleCommand(Builder builder, ProcessBuilder processBuilder) {
        String jriDir = "target/" + builder.finalName + "-jri";

        // FIXME instead of java, we should use the jriDir + "/bin/start" command with a switch such as "--modules"
        addModuleCommand(jriDir + "/bin/java",
                jriDir + "/app",
                builder.finalName,
                builder.moduleName,
                builder.mainClassModuleName,
                builder.mainClass,
                builder.args,
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
        command.add("--enable-preview");
        command.add("--module-path");
        command.add(location + "/" + finalName + ".jar" + File.pathSeparator + "target/libs");
        command.add("--module");
        command.add(mainClassModuleName + "/" + mainClass);

        if (!Objects.equals(moduleName, mainClassModuleName)) {
            // we are running main class from another module, need to add current module
            command.add("--add-modules");
            command.add(moduleName);
        }

        LOGGER.log(Level.DEBUG, () -> String.format("Command: %s", command));
        processBuilder.command(buildCommand(args, command));
    }

    private static void addJlinkCommand(Builder builder, ProcessBuilder processBuilder) {
        String jriDir = "target/" + builder.finalName + "-jri";
        // FIXME - instead of java -jar, we should use the jriDir + "/bin/start" command
        processBuilder.command(
                buildCommand(builder.args,
                        jriDir + "/bin/java",
                        "--enable-preview",
                        "-jar",
                        jriDir + "/app/" + builder.finalName + ".jar"));
    }

    private static void addClasspathCommand(Builder builder, ProcessBuilder processBuilder) {
        processBuilder.command(
                buildCommand(builder.args, "java", "--enable-preview", "-jar", "target/" + builder.finalName + ".jar"));
    }

    private static void addNativeCommand(Builder builder, ProcessBuilder processBuilder) {
        processBuilder.command(buildCommand(builder.args, "target/" + builder.finalName));
    }

    private static List<String> buildCommand(String[] args, String... command) {
        return buildCommand(args, Arrays.asList(command));
    }

    private static List<String> buildCommand(String[] args, List<String> command) {
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

        private static final Map<String, ExecType> NAMES = Arrays.stream(ExecType.values())
                .collect(Collectors.toMap(ExecType::toString, Function.identity()));

        /**
         * Get an exec type by name.
         *
         * @param name name
         * @return ExecType
         * @throws IllegalArgumentException if no exec type is found
         */
        public static ExecType of(String name) {
            ExecType execType = NAMES.get(name.toLowerCase());
            if (execType != null) {
                return execType;
            }
            throw new IllegalArgumentException("Unknown execType: " + name);
        }
    }

    /**
     * Builder for {@link HelidonProcessRunner}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, HelidonProcessRunner> {

        private ExecType execType = ExecType.CLASS_PATH;
        private String moduleName;
        private String mainClassModuleName;
        private String mainClass;
        private String finalName;
        private String[] args;
        private Runnable inMemoryStartCommand;
        private Runnable inMemoryStopCommand;

        /**
         * Set the type of execution.
         *
         * @param execType type of execution
         * @return this builder
         */
        public Builder execType(ExecType execType) {
            this.execType = execType;
            return this;
        }

        /**
         * Set the name of the application module (JPMS), used for {@link ExecType#MODULE_PATH}
         *
         * @param moduleName module name
         * @return this builder
         */
        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        /**
         * Set the name of the module of the main class (JPMS) - used for {@link ExecType#MODULE_PATH}
         *
         * @param mainClassModuleName module main class
         * @return this builder
         */
        public Builder mainClassModuleName(String mainClassModuleName) {
            this.mainClassModuleName = mainClassModuleName;
            return this;
        }

        /**
         * Set the name of the main class to run, used for {@link ExecType#MODULE_PATH}
         *
         * @param mainClass main class
         * @return this builder
         */
        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        /**
         * Set the final name of the artifact.
         * This is the expected name of the native image and jar file
         *
         * @param finalName final name
         * @return this builder
         */
        public Builder finalName(String finalName) {
            this.finalName = finalName;
            return this;
        }

        /**
         * Set the arguments passed to main method.
         *
         * @param args main args
         * @return this builder
         */
        public Builder args(String[] args) {
            this.args = args;
            return this;
        }

        /**
         * Set the command to start the server in memory, used for {@link ExecType#IN_MEMORY}
         *
         * @param inMemoryStartCommand in-memory start command
         * @return this builder
         */
        public Builder inMemoryStartCommand(Runnable inMemoryStartCommand) {
            this.inMemoryStartCommand = inMemoryStartCommand;
            return this;
        }

        /**
         * Set the command to stop the server in memory, used for {@link ExecType#IN_MEMORY}
         *
         * @param inMemoryStopCommand in-memory stop command
         * @return this builder
         */
        public Builder inMemoryStopCommand(Runnable inMemoryStopCommand) {
            this.inMemoryStopCommand = inMemoryStopCommand;
            return this;
        }

        @Override
        public HelidonProcessRunner build() {
            if (mainClassModuleName == null) {
                mainClassModuleName = moduleName;
            }
            return new HelidonProcessRunner(this);
        }
    }
}
