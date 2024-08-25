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

package io.helidon.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Static accessor to {@link io.helidon.testing.TestConfigSource} to be able to set values.
 */
@SuppressWarnings("removal")
public final class TestConfig {
    private static final ReentrantLock LOCK = new ReentrantLock();

    private TestConfig() {
    }

    /**
     * Set a configuration key and value.
     * This method CANNOT override an existing key, as such keys are already in the config snapshot.
     * <p>
     * If you need to override a key that exists in existing production configuration,
     * create a file that declares it in test resources with a reference to an unused key, such as {@code ${test.container.port}},
     * and then use this key to set the value at test time, usually in BeforeAll section of your test.
     *
     * @param key   key to set
     * @param value value to set
     */
    public static void set(String key, String value) {
        LOCK.lock();
        try {
            if (io.helidon.common.GlobalInstances.current(Sources.class).isPresent()) {
                io.helidon.common.GlobalInstances.get(Sources.class, Sources::new)
                        .sources()
                        .forEach(it -> it.set(key, value));
            } else {
                io.helidon.common.GlobalInstances.get(AccumulatedOptions.class, AccumulatedOptions::new)
                        .options().put(key, value);
            }
        } finally {
            LOCK.unlock();
        }
    }

    static void register(TestConfigSource source) {
        LOCK.lock();
        try {
            if (io.helidon.common.GlobalInstances.current(Sources.class).isEmpty()) {
                AccumulatedOptions accumulatedOptions = io.helidon.common.GlobalInstances.get(AccumulatedOptions.class,
                                                                                              AccumulatedOptions::new);
                accumulatedOptions.options().forEach(source::set);
                accumulatedOptions.options().clear();
            }
            io.helidon.common.GlobalInstances.get(Sources.class, Sources::new)
                    .sources()
                    .add(source);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Config profile to use.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Profile {
        /**
         * Config profile to set. This will always override profile set by other means.
         *
         * @return config profile to use
         */
        String value();
    }

    /**
     * Custom configuration key/value pair.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(ConfigValues.class)
    public @interface Value {
        /**
         * Key of the added configuration option.
         *
         * @return key
         */
        String key();

        /**
         * Value of the added configuration option.
         *
         * @return value
         */
        String value();

        /**
         * Weight of this configuration option.
         *
         * @return weight
         */
        double weight() default 954_000;
    }

    /**
     * Repeatable annotation for {@link io.helidon.testing.TestConfig.Value}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigValues {
        /**
         * Config values.
         *
         * @return values
         */
        Value[] value();
    }

    /**
     * Custom configuration key/value pairs.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Values {
        /**
         * Key of the added configuration option.
         *
         * @return key
         */
        String value();

        /**
         * Format of the value (file suffix).
         *
         * @return format to use, defaults to {@code properties}
         */
        String format() default "properties";

        /**
         * Weight of this configuration option.
         *
         * @return weight
         */
        double weight() default 953_000;
    }

    /**
     * Custom configuration file (classpath or file system, first looks for file system, second for file).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(ConfigFiles.class)
    public @interface File {
        /**
         * File path, relative to the module (either classpath, or file system path).
         *
         * @return path
         */
        String value();

        /**
         * Weight of this configuration option.
         *
         * @return weight
         */
        double weight() default 952_000;
    }

    /**
     * Repeatable annotation for {@link io.helidon.testing.TestConfig.File}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigFiles {
        /**
         * Config values.
         *
         * @return values
         */
        File[] value();
    }

    /**
     * Custom configuration source.
     * <p>
     * This annotation annotates a static method producing a {@link io.helidon.config.spi.ConfigSource}, or a list of
     * them.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Source {
        /**
         * Weight of this configuration option.
         *
         * @return weight
         */
        double weight() default 951_000;
    }

    private record Sources(List<TestConfigSource> sources) implements io.helidon.common.GlobalInstances.GlobalInstance {
        Sources() {
            this(new ArrayList<>());
        }

        @Override
        public void close() {

        }
    }

    private record AccumulatedOptions(Map<String, String> options) implements io.helidon.common.GlobalInstances.GlobalInstance {
        private AccumulatedOptions() {
            this(new HashMap<>());
        }

        @Override
        public void close() {
            options.clear();
        }
    }
}
