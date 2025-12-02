/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.metrics.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Interception;

/**
 * Annotations for declarative metrics.
 */
public final class Metric {
    private Metric() {
    }

    /**
     * The annotated method will be counted.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Documented
    @Interception.Intercepted
    public @interface Counted {
        /**
         * Name of the counter.
         *
         * @return counter name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the metric, defaults to {@link Meter.BaseUnits#NONE}.
         *
         * @return metric unit
         */
        String unit() default Meter.BaseUnits.NONE;
    }

    /**
     * The annotated method will be timed.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.METHOD)
    @Documented
    @Interception.Intercepted
    public @interface Timed {
        /**
         * Name of the timer.
         *
         * @return timer name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the metric, defaults to {@link Meter.BaseUnits#NANOSECONDS}.
         *
         * @return metric unit
         */
        String unit() default Meter.BaseUnits.NANOSECONDS;
    }

    /**
     * The annotated method will be a gauge source.
     */
    @Retention(RetentionPolicy.CLASS)
    @Documented
    @Interception.Intercepted
    @Target(ElementType.METHOD)
    public @interface Gauge {
        /**
         * Name of the gauge.
         *
         * @return gauge name
         */
        String value() default "";

        /**
         * Scope of the metric, defaults to {@link Meter.Scope#APPLICATION}.
         *
         * @return metric scope
         */
        String scope() default Meter.Scope.APPLICATION;

        /**
         * Description of the metric.
         *
         * @return metric description
         */
        String description() default "";

        /**
         * Additional tags of the metric.
         *
         * @return metric tags
         */
        Tag[] tags() default {};

        /**
         * Whether the defined name in {@link #value()} is an absolute name, or relative to the
         * class containing the annotated element.
         *
         * @return {@code true} if the name is absolute, {@code false} otherwise.
         */
        boolean absoluteName() default false;

        /**
         * Unit of the gauge must be provided.
         *
         * @return metric unit
         */
        String unit();
    }

    /**
     * Metric tag.
     * <p>
     * Tags can be declared on:
     * <ul>
     *     <li>The type that contains a metered method, such tags will be applied to all meters within this type</li>
     *     <li>The method that has a metric annotation, such tags will be applied to all meters on the method</li>
     *     <li>In meters annotations {@code tags} property, such tags will be applied only to the single meter</li>
     * </ul>
     */
    @Repeatable(Tags.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Tag {
        /**
         * Tag key.
         *
         * @return tag key
         */
        String key();

        /**
         * Tag value.
         *
         * @return tag value
         */
        String value();
    }

    /**
     * Container for {@link io.helidon.metrics.api.Metric.Tag} repeating annotation.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    public @interface Tags {
        /**
         * Tags of this repeating container.
         *
         * @return tags
         */
        Tag[] value();
    }
}
