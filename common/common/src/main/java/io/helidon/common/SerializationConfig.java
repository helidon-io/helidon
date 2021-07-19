/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Support for JEP 290 - deserialization filtering.
 * Configuration options mentioned below will differ in Helidon 3.0.0, the following table lists the options:
 * <p>
 * <table class="config">
 * <caption>Configuration Options</caption>
 * <tr>
 *     <th>system property</th>
 *     <th>2.x default</th>
 *     <th>3.x default</th>
 *     <th>description</th>
 * </tr>
 * <tr>
 *     <td>{@link #PROP_WRONG_CONFIG_ACTION}</td>
 *     <td>{@code warn} - {@link io.helidon.common.SerializationConfig.Action#WARN}</td>
 *     <td>{@code fail} - {@link io.helidon.common.SerializationConfig.Action#FAIL}</td>
 *     <td>What to do if an existing global deserialization filter exists without a global blacklist.</td>
 * </tr>
 * <tr>
 *     <td>{@link #PROP_NO_CONFIG_ACTION}</td>
 *     <td>{@code warn} - {@link io.helidon.common.SerializationConfig.Action#WARN}</td>
 *     <td>{@code configure} - {@link io.helidon.common.SerializationConfig.Action#CONFIGURE}</td>
 *     <td>What to do if there is no global deserialization filter.</td>
 * </tr>
 * </table>
 * Last option (not used by default) is to {@code ignore} the problem and do nothing (can be used both with wrong config
 * and no config above).
 * <h2>Deserialization filtering in Helidon</h2>
 * Helidon serialization filter is implemented to support whitelists, automatically blacklisting
 * all classes.
 * Helidon restrictions are only enforced on the global filter.
 * <h3>Custom pattern</h3>
 * To add patterns to the serial filter, use a system property {@value #PROP_PATTERN}.
 * This pattern follows the rules as defined by JDK. Helidon will add exclude all as the last
 * pattern.
 * <p>
 * As an alternative, a file {@link #PROPERTY_FILE} can be created on the classpath with the following content, to
 * configure filter for a specific library. Do not add a global blacklist to these patterns!:
 * {@code pattern=oracle.sql.converter.*}
 * <h3>Deserialization tracing</h3>
 * A tracing filter can be configured using system property {@value #PROP_TRACE} to log information
 * messages for each deserialization request.
 * <p>
 * To discover class patterns needed, set "no config" and "wrong config" actions to warn or ignore, and configure {@code basic}
 * tracing.
 * <p>
 * Options are:
 * <ul>
 *     <li>{@code none} to disable tracing (this is the default)</li>
 *     <li>{@code basic} to enable basic tracing (only traces requests for class deserialization)</li>
 *     <li>{@code full} to enable full tracing (including sizes, depth etc.)</li>
 * </ul>
 */
public final class SerializationConfig {
    static final String PROP_WRONG_CONFIG_ACTION = "helidon.serialFilter.failure.action";
    static final String PROP_NO_CONFIG_ACTION = "helidon.serialFilter.missing.action";
    static final String PROP_PATTERN = "helidon.serialFilter.pattern";
    static final String PROP_TRACE = "helidon.serialFilter.trace";
    static final String PROP_IGNORE_FILES = "helidon.serialFilter.ignoreFiles";
    private static final Logger LOGGER = Logger.getLogger(SerializationConfig.class.getName());
    private static final String PROPERTY_FILE = "META-INF/helidon/serial-config.properties";
    private static final AtomicReference<ConfigOptions> EXISTING_CONFIG = new AtomicReference<>();
    private final ConfigOptions options;

    private SerializationConfig(Builder builder) {
        this.options = new ConfigOptions(builder.onWrongConfig,
                                         builder.onNoConfig,
                                         builder.filterPattern,
                                         builder.traceSerialization);
    }

    /**
     * Fluent API builder to configure options programmatically.
     * To use defaults (or system properties), see {@link #configureRuntime()}.
     *
     * @return a new builder for {@link io.helidon.common.SerializationConfig}
     * @see #configure()
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Make sure configuration is as expected.
     * This is a one-off call to set up global filter.
     */
    public static void configureRuntime() {
        builder().build().configure();
    }

    /**
     * Configure deserialization filtering in the current VM.
     * Note that the global filter can be configured only once, so make sure this method is invoked as soon as possible.
     * This class keeps static information about the initial configuration, so as long as the configuration is unchanged,
     * this method may be called multiple times.
     *
     * @throws java.lang.IllegalStateException in case this method is called multiple times with different configuration.
     */
    public void configure() {
        if (EXISTING_CONFIG.compareAndSet(null, options)) {
            // this process is responsible for setting everything up, nobody else can reach this line

            ObjectInputFilter currentFilter = ObjectInputFilter.Config.getSerialFilter();

            if (currentFilter == null) {
                switch (options.onNoConfig()) {
                case FAIL:
                    throw new IllegalStateException("There is no global serial filter configured. To automatically configure"
                                                            + " a filter, please set system property " + PROP_NO_CONFIG_ACTION
                                                            + " to \"configure\"");
                case WARN:
                    AtomicBoolean logged = new AtomicBoolean();
                    configureTracingFilter(options, it -> {
                        if (it.serialClass() != null && logged.compareAndSet(false, true)) {
                            LOGGER.warning("Deserialization attempted for class " + it.serialClass().getName()
                                                   + ", yet there is no global serial filter configured. "
                                                   + "To automatically configure"
                                                   + " a filter, please set system property \"" + PROP_NO_CONFIG_ACTION
                                                   + "\" to \"configure\"");
                        }
                        return ObjectInputFilter.Status.UNDECIDED;
                    });
                    return;
                case IGNORE:
                    LOGGER.finest("Ignoring that there is no global serial filter configured. To automatically configure"
                                          + " a filter, please set system property " + PROP_NO_CONFIG_ACTION
                                          + " to \"configure\"");
                    configureTracingFilter(options, null);
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported no configuration action: " + options.onNoConfig());
                case CONFIGURE:
                    // this is the only option that continues with execution
                    configureGlobalFilter(options);
                    break;
                }
            } else {
                Action action = options.onWrongConfig();

                if (action == Action.IGNORE) {
                    LOGGER.finest("Existing serialization config is ignored by Helidon.");
                    return;
                }

                validateExistingFilter(currentFilter, action);
            }
        } else {
            ConfigOptions existingOptions = EXISTING_CONFIG.get();
            if (options.equals(existingOptions)) {
                return;
            }
            throw new IllegalArgumentException("You are trying to reconfigure serialization config with different options. "
                                                       + "This is not possible, as global filter can only be configured once."
                                                       + "Existing options: " + existingOptions + ", your options: " + options);
        }
    }

    ConfigOptions options() {
        return options;
    }

    private void validateExistingFilter(ObjectInputFilter currentFilter, Action action) {
        String currentFilterString = System.getProperty("jdk.serialFilter");

        if (currentFilterString == null) {
            LOGGER.finest("Programmatic filter configured: " + currentFilter);
            // somebody manually configured the filter
            ObjectInputFilter.Status status = currentFilter.checkInput(new ObjectInputFilter.FilterInfo() {
                @Override
                public Class<?> serialClass() {
                    return SerializationConfig.class;
                }

                @Override
                public long arrayLength() {
                    return 0;
                }

                @Override
                public long depth() {
                    return 0;
                }

                @Override
                public long references() {
                    return 0;
                }

                @Override
                public long streamBytes() {
                    return 0;
                }
            });
            if (status == ObjectInputFilter.Status.ALLOWED || status == ObjectInputFilter.Status.UNDECIDED) {
                handleBadFilter(action,
                                "Custom JDK Serialization Filter is not configured to blacklist all classes. "
                                        + "Helidon can only run with whitelists. Please add '!*' as the last "
                                        + "pattern.");
            }
        } else {
            LOGGER.finest("System property filter configured: " + currentFilterString);
            // make sure blacklist is for all
            if (currentFilterString.startsWith("!*;")
                    || currentFilterString.contains(";!*;")
                    || currentFilterString.endsWith(";!*")
                    || currentFilterString.equals("!*")) {
                // this is OK
                return;
            }
            handleBadFilter(action,
                            "jdk.serialFilter is configured without blacklisting all other classes. Helidon "
                                    + "can only run with whitelists. Please add '!*' as the last pattern.");
        }
    }

    private void handleBadFilter(Action action, String message) {
        switch (action) {
        case FAIL:
            throw new IllegalStateException(message);
        case WARN:
            LOGGER.warning(message);
            break;
        case CONFIGURE:
            throw new IllegalStateException("Cannot reconfigure current global deserialization filter."
                                                    + " Original message: " + message);
        case IGNORE:
            LOGGER.finest("Ignoring global deserialization filter issue. Original message: " + message);
            break;
        default:
            throw new IllegalStateException("Unexpected action to handle bad global deserialization filter: " + action);
        }
    }

    private void configureTracingFilter(ConfigOptions options, ObjectInputFilter existing) {
        ObjectInputFilter filter = existing;
        switch (options.traceSerialization()) {
        case BASIC:
            if (existing == null) {
                filter = emptyFilter();
            }
            ObjectInputFilter.Config.setSerialFilter(new TracingObjectInputFilter(filter, true));
            break;
        case FULL:
            if (existing == null) {
                filter = emptyFilter();
            }
            ObjectInputFilter.Config.setSerialFilter(new TracingObjectInputFilter(filter, false));
            break;
        case NONE:
            if (existing == null) {
                // no filter configured
                return;
            } else {
                ObjectInputFilter.Config.setSerialFilter(existing);
            }
            break;
        default:
            throw new IllegalArgumentException("Unsupported trace serialization option: " + options.traceSerialization());
        }
    }

    private ObjectInputFilter emptyFilter() {
        return new ObjectInputFilter() {
            @Override
            public Status checkInput(FilterInfo filterInfo) {
                return Status.UNDECIDED;
            }
        };
    }

    private void configureGlobalFilter(ConfigOptions options) {
        String pattern = options.filterPattern();
        LOGGER.finest("Using serialization pattern " + pattern);
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(pattern);

        configureTracingFilter(options, filter);
    }

    /**
     * What action to take if there is no global filter configured,
     * or if the configuration is not according to Helidon expectations.
     */
    public enum Action {
        /**
         * Fail by throwing an {@link java.lang.IllegalStateException}.
         */
        FAIL,
        /**
         * Warn in the log file.
         */
        WARN,
        /**
         * Attempt to configure the correct values.
         * Note that this may behave as {@link #FAIL} for cases where reconfiguration
         * is not possible.
         */
        CONFIGURE,
        /**
         * Ignore the problem and continue as if nothing happened.
         */
        IGNORE
    }

    /**
     * Deserialization tracing options.
     */
    public enum TraceOption {
        /**
         * Basic tracing will only trace attempts to deserialize a class, and only once for each class.
         */
        BASIC,
        /**
         * Full tracing traces any request to the deserialization filter.
         */
        FULL,
        /**
         * No deserialization tracing done.
         */
        NONE
    }

    /**
     * Fluent API builder to customize {@link io.helidon.common.SerializationConfig}.
     * You can use system properties defined in the class to modify configuration, in which case you can just use
     * {@link SerializationConfig#configureRuntime()} directly.
     */
    public static class Builder implements io.helidon.common.Builder<SerializationConfig> {
        private Action onWrongConfig = configuredAction(PROP_WRONG_CONFIG_ACTION, Action.WARN);
        private Action onNoConfig = configuredAction(PROP_NO_CONFIG_ACTION, Action.WARN);
        private String filterPattern = System.getProperty(PROP_PATTERN);
        private TraceOption traceSerialization = configuredTrace(TraceOption.NONE);
        private boolean ignoreFiles = Boolean.getBoolean(PROP_IGNORE_FILES);

        private Builder() {
        }

        private static Action configuredAction(String sysProp, Action defaultValue) {
            String property = System.getProperty(sysProp);
            if (property == null) {
                return defaultValue;
            }
            try {
                return Action.valueOf(property.toUpperCase());
            } catch (IllegalArgumentException e) {
                List<String> validActions = Arrays.stream(Action.values())
                        .map(Action::toString)
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());

                LOGGER.warning("System property \"" + sysProp + "\" is configured to \"" + property + "\", which is"
                                       + " not a valid Action. Valid actions: " + validActions
                                       + ". Using: " + defaultValue.toString().toLowerCase());

                return defaultValue;
            }
        }

        private static TraceOption configuredTrace(TraceOption defaultValue) {
            String property = System.getProperty(PROP_TRACE);
            if (property == null) {
                return defaultValue;
            }
            try {
                return TraceOption.valueOf(property.toUpperCase());
            } catch (IllegalArgumentException e) {
                List<String> validTraceOptions = Arrays.stream(TraceOption.values())
                        .map(TraceOption::toString)
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());

                LOGGER.warning("System property \"" + PROP_TRACE + "\" is configured to \"" + property + "\", which is"
                                       + " not a valid TraceOption. Valid trace options: " + validTraceOptions
                                       + ". Using: " + defaultValue.toString().toLowerCase());

                return defaultValue;
            }
        }

        @Override
        public SerializationConfig build() {
            this.filterPattern = getPattern();
            return new SerializationConfig(this);
        }

        /**
         * What action to do in case of wrong configuration of the global filter.
         *
         * @param onWrongConfig action to do
         * @return updated builder
         */
        public Builder onWrongConfig(Action onWrongConfig) {
            this.onWrongConfig = onWrongConfig;
            return this;
        }

        /**
         * What action to do in case of no configuration of the global filter.
         *
         * @param onNoConfig action to do
         * @return updated builder
         */
        public Builder onNoConfig(Action onNoConfig) {
            this.onNoConfig = onNoConfig;
            return this;
        }

        /**
         * Filter pattern to use.
         *
         * @param filterPattern filter pattern
         * @return updated builder
         */
        public Builder filterPattern(String filterPattern) {
            this.filterPattern = filterPattern;
            return this;
        }

        /**
         * How to trace serialization.
         *
         * @param traceSerialization trace option
         * @return updated builder
         */
        public Builder traceSerialization(TraceOption traceSerialization) {
            this.traceSerialization = traceSerialization;
            return this;
        }

        /**
         * Whether to ignore {@value io.helidon.common.SerializationConfig#PROPERTY_FILE} property files defined in
         * dependencies.
         *
         * @param ignoreFiles {@code true} to ignore files on classpath, defaults to {@code false}
         * @return updated builder
         */
        public Builder ignoreFiles(boolean ignoreFiles) {
            this.ignoreFiles = ignoreFiles;
            return this;
        }

        private String getPattern() {
            // first make sure we do not conflict with default JDK configuration options
            String currentFilterString = System.getProperty("jdk.serialFilter");
            if (currentFilterString != null) {
                if (filterPattern != null && !filterPattern.isBlank()) {
                    throw new IllegalArgumentException("jdk.serialFilter system property is configured and an explicit"
                                                               + " filter pattern is configured as well. This is not supported.");
                }
                return "!*";
            }

            if (ignoreFiles) {
                if (filterPattern == null || filterPattern.isBlank()) {
                    return "!*";
                }
                return filterPattern + ";!*";
            }

            List<String> parts = new LinkedList<>();

            try {
                Enumeration<URL> resources = SerializationConfig.class
                        .getClassLoader()
                        .getResources(PROPERTY_FILE);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    Properties props = new Properties();
                    props.load(url.openStream());

                    String pattern = props.getProperty("pattern");
                    if (pattern == null) {
                        LOGGER.warning("Could not find 'pattern' property in " + url);
                    } else {
                        if (!pattern.isBlank()) {
                            parts.add(pattern);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not find " + PROPERTY_FILE + " resources", e);
            }

            if (!(filterPattern == null || filterPattern.isBlank())) {
                parts.add(filterPattern.trim());
            }
            parts.add("!*");

            return String.join(";", parts);
        }
    }

    static final class ConfigOptions {
        private final Action onWrongConfig;
        private final Action onNoConfig;
        private final String filterPattern;
        private final TraceOption traceSerialization;

        private ConfigOptions(Action onWrongConfig,
                              Action onNoConfig,
                              String filterPattern,
                              TraceOption traceSerialization) {
            this.onWrongConfig = onWrongConfig;
            this.onNoConfig = onNoConfig;
            this.filterPattern = filterPattern;
            this.traceSerialization = traceSerialization;
        }

        Action onWrongConfig() {
            return onWrongConfig;
        }

        Action onNoConfig() {
            return onNoConfig;
        }

        String filterPattern() {
            return filterPattern;
        }

        TraceOption traceSerialization() {
            return traceSerialization;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigOptions that = (ConfigOptions) o;
            return onWrongConfig == that.onWrongConfig && onNoConfig == that.onNoConfig && filterPattern
                    .equals(that.filterPattern) && traceSerialization == that.traceSerialization;
        }

        @Override
        public int hashCode() {
            return Objects.hash(onWrongConfig, onNoConfig, filterPattern, traceSerialization);
        }

        @Override
        public String toString() {
            return "ConfigOptions{"
                    + "onWrongConfig=" + onWrongConfig
                    + ", onNoConfig=" + onNoConfig
                    + ", filterPattern='" + filterPattern + '\''
                    + ", traceSerialization=" + traceSerialization
                    + '}';
        }
    }

    private static class TracingObjectInputFilter implements ObjectInputFilter {
        private static final Logger LOGGER = Logger.getLogger(TracingObjectInputFilter.class.getName());

        private final Set<Class<?>> reportedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final ObjectInputFilter delegate;
        private final boolean basic;

        private TracingObjectInputFilter(ObjectInputFilter filter, boolean basic) {
            this.delegate = filter;
            this.basic = basic;
        }

        @Override
        public Status checkInput(FilterInfo filterInfo) {
            Class<?> clazz = filterInfo.serialClass();
            if (clazz == null && basic) {
                return delegate.checkInput(filterInfo);
            }
            Status result = delegate.checkInput(filterInfo);

            if (!reportedClasses.add(clazz)) {
                if (basic) {
                    return result;
                }
            }
            LOGGER.info(result
                                + " class: " + clazz
                                + ", arrayLength: " + filterInfo.arrayLength()
                                + ", depth: " + filterInfo.depth()
                                + ", references: " + filterInfo.references()
                                + ", streamBytes: " + filterInfo.streamBytes());

            return result;
        }
    }
}
