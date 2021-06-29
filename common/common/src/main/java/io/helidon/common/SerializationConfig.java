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
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support for JEP 290 - deserialization filtering.
 * Helidon is implemented to support whitelists, automatically blacklisting
 * all classes.
 * Helidon restrictions are only enforced on the global filter.
 * <p>
 * To add patterns to the serial filter, use a system property {@value #PROP_PATTERN}.
 * This pattern follows the rules as defined by JDK. Helidon will add exclude all as the last
 * pattern.
 * <p>
 * To ignore this configuration, use system property {@value #PROP_IGNORE} and set it to {@code true}.
 * <p>
 * A tracing filter can be configured using system property {@value #PROP_TRACE} to log information
 * messages for each deserialization request.
 */
public final class SerializationConfig {
    private static final Logger LOGGER = Logger.getLogger(SerializationConfig.class.getName());
    private static final String PROP_IGNORE = "helidon.serialFilter.ignore";
    private static final String PROP_PATTERN = "helidon.serialFilter.pattern";
    private static final String PROP_TRACE = "helidon.serialFilter.trace";
    private static final String PROPERTY = "META-INF/helidon/serial-config.properties";

    private SerializationConfig() {
    }

    private static final AtomicBoolean HELIDON_CONFIGURED = new AtomicBoolean();

    /**
     * Make sure configuration is as expected.
     */
    public static void configureRuntime() {
        ObjectInputFilter currentFilter = ObjectInputFilter.Config.getSerialFilter();

        if (currentFilter == null) {
            if ("true".equals(System.getProperty(PROP_IGNORE))) {
                LOGGER.finest("Serialization config is ignored by Helidon.");
                // explicitly configured to be ignored
                HELIDON_CONFIGURED.set(true);
                return;
            }
            String pattern = getPattern();
            LOGGER.finest("Using serialization pattern " + pattern);
            ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(pattern);
            if ("true".equals(System.getProperty(PROP_TRACE))) {
                ObjectInputFilter.Config.setSerialFilter(new TracingObjectInputFilter(filter));
            } else {
                ObjectInputFilter.Config.setSerialFilter(filter);
            }
            HELIDON_CONFIGURED.set(true);
        } else {
            if (HELIDON_CONFIGURED.get()) {
                // it was us
                return;
            }

            if ("true".equals(System.getProperty(PROP_IGNORE))) {
                LOGGER.finest("Serialization config is ignored by Helidon.");
                // explicitly configured to be ignored
                HELIDON_CONFIGURED.set(true);
                return;
            }

            String currentFilterString = System.getProperty("jdk.serialFilter");
            if (System.getProperty(PROP_PATTERN) != null) {
                LOGGER.warning("You have defined " + PROP_PATTERN + " system property, yet serial filter is "
                                       + " already configured "
                                       + (currentFilterString == null ? " via jdk.serialFilter" : " programmatically"));
            }

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
                    throw new IllegalStateException("Custom JDK Serialization Filter is not configured to blacklist all classes. "
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
                throw new IllegalStateException("jdk.serialFilter is configured without blacklisting all other classes. Helidon "
                                                        + "can only run with whitelists. Please add '!*' as the last pattern.");
            }
        }
    }

    private static String getPattern() {
        List<String> parts = new LinkedList<>();

        try {
            Enumeration<URL> resources = SerializationConfig.class
                    .getClassLoader()
                    .getResources(PROPERTY);
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
            LOGGER.log(Level.WARNING, "Could not find " + PROPERTY + " resources", e);
        }

        String pattern = System.getProperty(PROP_PATTERN);
        if (!(pattern == null || pattern.isBlank())) {
            parts.add(pattern.trim());
        }
        parts.add("!*");

        return String.join(";", parts);
    }

    private static class TracingObjectInputFilter implements ObjectInputFilter {
        private static final Logger LOGGER = Logger.getLogger(TracingObjectInputFilter.class.getName());
        private final ObjectInputFilter delegate;

        private TracingObjectInputFilter(ObjectInputFilter filter) {
            this.delegate = filter;
        }

        @Override
        public Status checkInput(FilterInfo filterInfo) {
            Status result = delegate.checkInput(filterInfo);

            LOGGER.info(result
                                + " class: " + filterInfo.serialClass()
                                + ", arrayLength: " + filterInfo.arrayLength()
                                + ", depth: " + filterInfo.depth()
                                + ", references: " + filterInfo.references()
                                + ", streamBytes: " + filterInfo.streamBytes());

            return result;
        }
    }
}
