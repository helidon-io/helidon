/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.testing.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Helidon testing related annotations and APIs.
 */
public final class Testing {
    private Testing() {
    }

    /**
     * A test class annotation that ensures Helidon extension is loaded.
     */
    @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @ExtendWith(TestJunitExtension.class)
    public @interface Test {
        /**
         * If set to {@code true}, service registry will be reset after each test method, instead after the whole test class.
         *
         * @return whether to reset registry after each test method
         */
        boolean perMethod() default false;
    }

    /**
     * Add a configuration fragment to the {@link Configuration#useExisting() synthetic test configuration}.
     * <p>
     * Example:
     * <pre>
     *  &#64;AddConfigBlock(type = "yaml", value = """
     *      foo1:
     *        bar: "value1"
     *  """)
     *  &#64;AddConfigBlock(type = "properties", value = """
     *       foo2=value2
     *       foo3=value3
     *  """)
     *  class MyTest {
     *  }
     * </pre>
     * <p>
     * This annotation can be repeated.
     * <p>
     * If used on a method, the container will be reset regardless of the test lifecycle.
     *
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(AddConfigBlocks.class)
    public @interface AddConfigBlock {
        /**
         * Specifies the configuration format. Possible values are: 'yaml' and 'properties'.
         * <p>
         * The default format is 'properties'
         *
         * @return the supported type
         */
        String type() default "properties";

        /**
         * Configuration fragment.
         *
         * @return fragment
         */
        String value();
    }

    /**
     * A repeatable container for {@link AddConfigBlock}.
     * <p>
     * This annotation is optional, you can instead repeat {@link AddConfigBlock}.
     * <p>
     * E.g.
     * <pre>
     * &#64;AddConfigBlock(type = "yaml", value = """
     *     foo1:
     *       bar: "value1"
     * """)
     * &#64;AddConfigBlock(type = "yaml", value = """
     *     foo2:
     *       bar: "value2"
     * """)
     * class MyTest {
     * }
     * </pre>
     *
     * @see AddConfigBlock
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface AddConfigBlocks {
        /**
         * Get the contained annotations.
         *
         * @return annotations
         */
        AddConfigBlock[] value();
    }

    /**
     * General setting for the test configuration.
     * <p>
     * If used on a method, the container will be reset regardless of the test lifecycle.
     *
     * @see AddConfigBlock
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Configuration {
        /**
         * If set to {@code false}, the synthetic test configuration is used.
         * <p>
         * The synthetic test configuration is expressed with the following:
         * <ul>
         *     <li>{@link #configSources()}</li>
         *     <li>{@link AddConfigBlock}</li>
         * </ul>
         * <p>
         * If set to {@code true}, only the existing (or default) configuration is used as-is
         * and the annotations listed previously are ignored.
         *
         * @return whether to use an existing (or default) configuration
         */
        boolean useExisting() default false;

        /**
         * Class-path resources to add as config sources to the synthetic test configuration.
         *
         * @return config sources
         */
        String[] configSources() default {};
    }
}
