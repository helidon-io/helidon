package io.helidon.tests.integration.nativeimage.se1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class HelidonTestProcess {
    private static final Logger LOGGER = Logger.getLogger(HelidonTestProcess.class.getName());

    private final ExecType execType;
    private final Runnable startCommand;
    private final Runnable stopCommand;
    private Process process;

    public HelidonTestProcess(ExecType execType, Runnable startCommand, Runnable stopCommand) {
        this.execType = execType;
        this.startCommand = startCommand;
        this.stopCommand = stopCommand;
    }

    public void startApplication() {
        startCommand.run();
    }

    public ExecType execType() {
        return execType;
    }

    public void stopApplication() {
        stopCommand.run();
        if (process != null) {
            process.destroy();
        }
    }

    public static HelidonTestProcess create(String mainClassModuleName,
                                            Class<?> mainClass,
                                            String finalName,
                                            Runnable inMemoryStartCommand,
                                            Runnable inMemoryStopCommand) {
        HelidonTestProcess.ExecType execType = ExecType
                .fromSystemProperty("helidon.runtime.type", ExecType.IN_MEMORY);

        ProcessBuilder processBuilder = new ProcessBuilder();
        // ensure we run on a random port
        processBuilder.environment().put("SERVER_PORT", "-1");

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
        case IN_MEMORY:
            return new HelidonTestProcess(execType, inMemoryStartCommand, inMemoryStopCommand);
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

                Thread.sleep(500);

                if (!process.get().isAlive()) {
                    throw new RuntimeException("Process failed to start. Exit code: " + process.get().exitValue());
                }
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

        return new HelidonTestProcess(execType, startCommand, stopCommand);
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

    private static void addModulePathCommand(String finalName, String moduleName, Class<?> mainClass, ProcessBuilder processBuilder) {
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

    public enum ExecType {
        CLASS_PATH("classpath"),
        MODULE_PATH("module"),
        NATIVE("native"),
        IN_MEMORY("memory");

        private final String name;

        ExecType(String name) {
            this.name = name;
        }

        static ExecType fromSystemProperty(String propertyName, ExecType defaultType) {
            String propertyValue = System.getProperty(propertyName);
            if (propertyValue == null || propertyValue.isBlank()) {
                return defaultType;
            }
            propertyValue = propertyValue.toLowerCase();

            for (ExecType value : ExecType.values()) {
                if (value.name.equals(propertyValue)) {
                    return value;
                }
            }

            throw new IllegalArgumentException("Invalid type, must be one of \"classpath, module, native\", but was: " + propertyValue);
        }

        public String valueInConfig() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
