/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Fluent process runner.
 */
@SuppressWarnings("unused")
public abstract class ProcessRunner {

    private static final boolean IS_WINDOWS = File.pathSeparatorChar != ':';
    private static final Pattern VAR_PATTERN = Pattern.compile("[^$]*\\$\\{(?<varName>[^}]+)}[^$]*");

    private final AtomicReference<ProcessMonitor> ref = new AtomicReference<>();
    private final List<Supplier<Map<String, ?>>> lazyProps = new ArrayList<>();
    private final List<Supplier<Map<String, ?>>> lazyEnv = new ArrayList<>();
    private final List<Supplier<List<?>>> lazyOpts = new ArrayList<>();
    private final List<Supplier<List<?>>> lazyArgs = new ArrayList<>();

    protected String moduleName;
    protected String mainModule;
    protected String mainClass;
    protected String finalName;
    protected WaitStrategy waitStrategy = WaitStrategy.waitForCompletion();
    protected int port = -1;

    private ProcessRunner() {
    }

    /**
     * Execution mode.
     */
    public enum ExecMode {
        /**
         * Execute processes using class path.
         */
        CLASS_PATH,

        /**
         * Execute processes using module path.
         */
        MODULE_PATH,

        /**
         * Execute native processes.
         */
        NATIVE,

        /**
         * Execute processes using the Helidon jlink start script.
         */
        JLINK_CLASS_PATH,

        /**
         * Execute processes using the Helidon jlink distribution with module path.
         */
        JLINK_MODULE_PATH
    }

    /**
     * Create a new process runner.
     *
     * @param execMode execution mode
     * @return ProcessRunner
     */
    public static ProcessRunner of(ExecMode execMode) {
        return switch (execMode) {
            case CLASS_PATH -> new ClassPathRunner();
            case NATIVE -> new NativeImageRunner();
            case MODULE_PATH -> new ModulePathRunner();
            case JLINK_CLASS_PATH -> new JlinkRunner();
            case JLINK_MODULE_PATH -> new JlinkModulePathRunner();
        };
    }

    /**
     * Get the last started process.
     *
     * @return ProcessMonitor
     * @throws IllegalStateException if the process is not started
     */
    public ProcessMonitor process() {
        ProcessMonitor process = ref.get();
        if (process == null) {
            throw new IllegalStateException("Process is not started");
        }
        return process;
    }

    /**
     * Set the port.
     *
     * @param port port, {@code 0} overrides the configured port with a free port
     * @return this instance
     */
    public ProcessRunner port(int port) {
        this.port = port;
        return this;
    }

    /**
     * Set environment variables.
     *
     * @param env environment variables
     * @return this instance
     */
    public ProcessRunner env(Map<String, ?> env) {
        return env(() -> env);
    }

    /**
     * Set environment variables.
     *
     * @param env environment variables
     * @return this instance
     */
    public ProcessRunner env(Supplier<Map<String, ?>> env) {
        this.lazyEnv.add(env);
        return this;
    }

    /**
     * Set system properties.
     *
     * @param props properties
     * @return this instance
     */
    public ProcessRunner properties(Map<String, ?> props) {
        return properties(() -> props);
    }

    /**
     * Set system properties.
     *
     * @param props properties
     * @return this instance
     */
    public ProcessRunner properties(Supplier<Map<String, ?>> props) {
        this.lazyProps.add(props);
        return this;
    }

    /**
     * Set the arguments passed to main method.
     *
     * @param args main args
     * @return this instance
     */
    public ProcessRunner args(String... args) {
        return args(() -> Arrays.asList(args));
    }

    /**
     * Set the arguments passed to main method.
     *
     * @param args main args
     * @return this instance
     */
    public ProcessRunner args(List<Object> args) {
        return args(() -> args);
    }

    /**
     * Set the arguments passed to main method.
     *
     * @param args main args
     * @return this instance
     */
    public ProcessRunner args(Supplier<List<?>> args) {
        this.lazyArgs.add(args);
        return this;
    }

    /**
     * Set the options passed to the executable.
     *
     * @param opts opts
     * @return this instance
     */
    public ProcessRunner opts(String... opts) {
        return opts(() -> Arrays.asList(opts));
    }

    /**
     * Set the options passed to the executable.
     *
     * @param opts opts
     * @return this instance
     */
    public ProcessRunner opts(List<Object> opts) {
        return opts(() -> opts);
    }

    /**
     * Set the options passed to the executable.
     *
     * @param opts opts
     * @return this instance
     */
    public ProcessRunner opts(Supplier<List<?>> opts) {
        this.lazyOpts.add(opts);
        return this;
    }

    /**
     * Set the name of the application module (JPMS), used for
     * {@link ProcessRunner.ExecMode#MODULE_PATH}
     *
     * @param moduleName module name
     * @return this instance
     */
    public ProcessRunner moduleName(String moduleName) {
        this.moduleName = requireNonNull(moduleName, "moduleName is null");
        return this;
    }

    /**
     * Set the name of the module of the main class (JPMS) - used for
     * {@link ProcessRunner.ExecMode#MODULE_PATH}
     *
     * @param mainModule main module
     * @return this instance
     */
    public ProcessRunner mainModule(String mainModule) {
        this.mainModule = requireNonNull(mainModule, "mainModule is null");
        return this;
    }

    /**
     * Set the name of the main class to run, used for
     * {@link ProcessRunner.ExecMode#MODULE_PATH}
     *
     * @param mainClass main class
     * @return this instance
     */
    public ProcessRunner mainClass(String mainClass) {
        this.mainClass = requireNonNull(mainClass, "mainClass is null");
        return this;
    }

    /**
     * Set the final name of the artifact.
     * This is the expected name of the native image and jar file
     *
     * @param finalName final name
     * @return this instance
     */
    public ProcessRunner finalName(String finalName) {
        this.finalName = requireNonNull(finalName, "finalName is null");
        return this;
    }

    /**
     * Set the waiting strategy.
     *
     * @param waitStrategy waiting strategy
     * @return this builder
     * @see WaitStrategy
     */
    public ProcessRunner waitingFor(WaitStrategy waitStrategy) {
        this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy is null");
        return this;
    }

    /**
     * Build the command.
     *
     * @param opts resolved options
     * @param args resolved args
     * @return command
     */
    protected abstract List<String> command(List<String> opts, List<String> args);

    /**
     * Start the process.
     *
     * @return ProcessMonitor
     */
    public ProcessMonitor start() {
        int port = resolvePort();
        List<String> opts = resolveOpts(port);
        List<String> args = toList(lazyArgs);
        Map<String, ?> env = resolveEnv(port);
        ProcessBuilder pb = new ProcessBuilder(command(opts, args));
        env.forEach((k, v) -> pb.environment().put(k, toString(v)));
        ProcessMonitor monitor = new ProcessMonitor(pb, port, waitStrategy);
        ref.set(monitor);
        return monitor;
    }

    private int resolvePort() {
        if (port == 0) {
            try (ServerSocket socket = new ServerSocket(0)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to get free port", e);
            }
        }
        return port;
    }

    private Map<String, Object> resolveProps(int port) {
        Map<String, Object> props = toMap(lazyProps);
        if (port > 0) {
            props.putIfAbsent("server.port", port);
        }
        return props;
    }

    private Map<String, ?> resolveEnv(int port) {
        Map<String, Object> env = toMap(lazyEnv);
        if (port > 0) {
            env.putIfAbsent("SERVER_PORT", port);
        }
        return env;
    }

    private List<String> resolveOpts(int port) {
        List<String> opts = toList(lazyOpts);
        Map<String, Object> props = resolveProps(port);
        resolve(props).forEach((k, v) -> opts.add("-D" + k + "=" + v));
        return opts;
    }

    private static List<String> toList(List<Supplier<List<?>>> lazy) {
        List<String> list = new ArrayList<>();
        lazy.forEach(m -> m.get().forEach(v -> {
            if (v instanceof Supplier<?> s) {
                list.add(toString(s.get()));
            } else {
                list.add(toString(v));
            }
        }));
        return list;
    }

    private static Map<String, Object> toMap(List<Supplier<Map<String, ?>>> lazy) {
        Map<String, Object> map = new HashMap<>();
        lazy.forEach(m -> m.get().forEach((k, v) -> {
            if (v instanceof Supplier<?> s) {
                map.put(k, s.get());
            } else {
                map.put(k, v);
            }
        }));
        return map;
    }

    private static String toString(Object o) {
        if (o instanceof Path path) {
            return path.toAbsolutePath().normalize().toString();
        }
        return o.toString();
    }

    private static Map<String, String> resolve(Map<String, Object> entries) {
        Map<String, String> map = new HashMap<>();
        entries.forEach((k, v) -> map.put(k, resolve(toString(v), s -> toString(entries.getOrDefault(s, "")))));
        return map;
    }

    private static String resolve(String str, Function<String, String> function) {
        StringBuilder sb = new StringBuilder();
        Matcher m = VAR_PATTERN.matcher(str);
        int index = 0;
        while (m.find()) {
            sb.append(str, index, m.start(1) - 2);
            sb.append(function.apply(m.group(1)));
            index = m.end(1) + 1;
        }
        sb.append(str.substring(index));
        return sb.toString();
    }

    private static String javaExecutable() {
        return PathFinder.find("java", "JAVA_HOME")
                .map(Path::toString)
                .orElseThrow(() -> new IllegalStateException("Unable to find java"));
    }

    private static final class ClassPathRunner extends ProcessRunner {

        @Override
        protected List<String> command(List<String> opts, List<String> args) {
            Objects.requireNonNull(finalName, "finalName is null");
            return new CommandBuilder(javaExecutable())
                    .append(opts)
                    .append("-jar", Path.of("target/" + finalName + ".jar"))
                    .append(args)
                    .command();
        }
    }

    private static class ModulePathRunner extends ProcessRunner {

        Path dir() {
            return Path.of("target");
        }

        @Override
        protected List<String> command(List<String> opts, List<String> args) {
            Objects.requireNonNull(mainClass, "mainClass is null");
            Objects.requireNonNull(moduleName, "moduleName is null");
            Objects.requireNonNull(finalName, "finalName is null");
            CommandBuilder cb = new CommandBuilder(javaExecutable());
            String module = mainModule != null ? mainModule : moduleName;
            cb.append(opts)
                    .append("--module-path")
                    .append(dir().resolve(finalName + ".jar") + File.pathSeparator + dir().resolve("libs"))
                    .append("--module")
                    .append(module + "/" + mainClass);
            if (!Objects.equals(module, moduleName)) {
                // we are running main class from another module, need to add current module
                cb.append("--add-modules").append(moduleName);
            }
            return cb.command();
        }
    }

    private static final class NativeImageRunner extends ProcessRunner {

        @Override
        protected List<String> command(List<String> opts, List<String> args) {
            Objects.requireNonNull(finalName, "finalName is null");
            return new CommandBuilder("target/" + finalName + (IS_WINDOWS ? ".exe" : ""))
                    .append(opts)
                    .append(args)
                    .command();
        }
    }

    private static final class JlinkRunner extends ProcessRunner {

        @Override
        protected List<String> command(List<String> opts, List<String> args) {
            Objects.requireNonNull(finalName, "finalName is null");
            return new CommandBuilder("target/" + finalName + "-jri/bin/start" + (IS_WINDOWS ? ".ps1" : ""))
                    .append("--jvm", String.join(" ", opts))
                    .append(args)
                    .command();
        }
    }

    private static final class JlinkModulePathRunner extends ModulePathRunner {

        @Override
        protected Path dir() {
            return Path.of("target/" + finalName + "-jri/app");
        }
    }

    private record CommandBuilder(List<String> command) {

        CommandBuilder(String bin) {
            this(new ArrayList<>(List.of(Path.of(bin).toString())));
        }

        CommandBuilder append(List<?> fragments) {
            fragments.forEach(f -> command.add(f.toString()));
            return this;
        }

        CommandBuilder append(Object... fragments) {
            return append(Arrays.asList(fragments));
        }
    }
}
