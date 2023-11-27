/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Errors;

/**
 * An event happening during code gen to be logged with {@link io.helidon.codegen.CodegenLogger#log(CodegenEvent)}.
 * This is not a fast solution, it is only to be used when processing code, where
 * we can have a bit of an overhead!
 *
 * @see #builder()
 */
public interface CodegenEvent extends CodegenEventBlueprint {

    /**
     * Create a new fluent API builder to customize configuration.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new fluent API builder from an existing instance.
     *
     * @param instance an existing instance used as a base for the builder
     * @return a builder based on an instance
     */
    static Builder builder(CodegenEvent instance) {
        return CodegenEvent.builder().from(instance);
    }

    /**
     * Fluent API builder base for {@link io.helidon.codegen.CodegenEvent}.
     *
     * @param <BUILDER> type of the builder extending this abstract builder
     */
    abstract class BuilderBase<BUILDER extends BuilderBase<BUILDER>> implements io.helidon.common.Builder<BUILDER, CodegenEvent> {

        private final List<Object> objects = new ArrayList<>();
        private System.Logger.Level level = System.Logger.Level.INFO;
        private String message;
        private Throwable throwable;

        /**
         * Protected to support extensibility.
         */
        protected BuilderBase() {
        }

        /**
         * Update this builder from an existing prototype instance.
         *
         * @param prototype existing prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(CodegenEvent prototype) {
            level(prototype.level());
            message(prototype.message());
            throwable(prototype.throwable());
            addObjects(prototype.objects());
            return identity();
        }

        /**
         * Update this builder from an existing prototype builder instance.
         *
         * @param builder existing builder prototype to update this builder from
         * @return updated builder instance
         */
        public BUILDER from(BuilderBase<?> builder) {
            level(builder.level());
            builder.message().ifPresent(this::message);
            builder.throwable().ifPresent(this::throwable);
            addObjects(builder.objects());
            return identity();
        }

        /**
         * Level can be used directly (command line tools), mapped to Maven level (maven plugins),
         * or mapped to diagnostics kind (annotation processing).
         * <p>
         * Mapping table:
         * <table>
         *     <caption>Level mappings</caption>
         *     <tr>
         *         <th>Level</th>
         *         <th>Maven log level</th>
         *         <th>APT Diagnostic.Kind</th>
         *     </tr>
         *     <tr>
         *         <td>ERROR</td>
         *         <td>error</td>
         *         <td>ERROR</td>
         *     </tr>
         *     <tr>
         *         <td>WARNING</td>
         *         <td>warn</td>
         *         <td>WARNING</td>
         *     </tr>
         *     <tr>
         *         <td>INFO</td>
         *         <td>info</td>
         *         <td>NOTE</td>
         *     </tr>
         *     <tr>
         *         <td>DEBUG, TRACE</td>
         *         <td>debug</td>
         *         <td>N/A - only logged to logger</td>
         *     </tr>
         * </table>
         *
         * @param level level to use, defaults to INFO
         * @return updated builder instance
         * @see #level()
         */
        public BUILDER level(System.Logger.Level level) {
            Objects.requireNonNull(level);
            this.level = level;
            return identity();
        }

        /**
         * Message to be delivered to the user.
         *
         * @param message the message
         * @return updated builder instance
         * @see #message()
         */
        public BUILDER message(String message) {
            Objects.requireNonNull(message);
            this.message = message;
            return identity();
        }

        /**
         * Clear existing value of this property.
         *
         * @return updated builder instance
         * @see #throwable()
         */
        public BUILDER clearThrowable() {
            this.throwable = null;
            return identity();
        }

        /**
         * Throwable if available.
         *
         * @param throwable throwable
         * @return updated builder instance
         * @see #throwable()
         */
        public BUILDER throwable(Throwable throwable) {
            Objects.requireNonNull(throwable);
            this.throwable = throwable;
            return identity();
        }

        /**
         * Additional information, such as source elements.
         * These may or may not be ignored by the final log destination.
         * <p>
         * Expected supported types:
         * <ul>
         *     <li>APT: {@code Element}, {@code AnnotationMirror}, {@code AnnotationValue}</li>
         *     <li>Classpath scanning: {@code ClassInfo}, {@code MethodInfo} etc.</li>
         *     <li>Any environment: {@link io.helidon.common.types.TypeName},
         *     {@link io.helidon.common.types.TypeInfo},
         *     or {@link io.helidon.common.types.TypedElementInfo}</li>
         * </ul>
         *
         * @param objects list of objects causing this event to happen
         * @return updated builder instance
         * @see #objects()
         */
        public BUILDER objects(List<?> objects) {
            Objects.requireNonNull(objects);
            this.objects.clear();
            this.objects.addAll(objects);
            return identity();
        }

        /**
         * Additional information, such as source elements.
         * These may or may not be ignored by the final log destination.
         * <p>
         * Expected supported types:
         * <ul>
         *     <li>APT: {@code Element}, {@code AnnotationMirror}, {@code AnnotationValue}</li>
         *     <li>Classpath scanning: {@code ClassInfo}, {@code MethodInfo} etc.</li>
         *     <li>Any environment: {@link io.helidon.common.types.TypeName},
         *     {@link io.helidon.common.types.TypeInfo},
         *     or {@link io.helidon.common.types.TypedElementInfo}</li>
         * </ul>
         *
         * @param objects list of objects causing this event to happen
         * @return updated builder instance
         * @see #objects()
         */
        public BUILDER addObjects(List<?> objects) {
            Objects.requireNonNull(objects);
            this.objects.addAll(objects);
            return identity();
        }

        /**
         * Additional information, such as source elements.
         * These may or may not be ignored by the final log destination.
         * <p>
         * Expected supported types:
         * <ul>
         *     <li>APT: {@code Element}, {@code AnnotationMirror}, {@code AnnotationValue}</li>
         *     <li>Classpath scanning: {@code ClassInfo}, {@code MethodInfo} etc.</li>
         *     <li>Any environment: {@link io.helidon.common.types.TypeName},
         *     {@link io.helidon.common.types.TypeInfo},
         *     or {@link io.helidon.common.types.TypedElementInfo}</li>
         * </ul>
         *
         * @param object list of objects causing this event to happen
         * @return updated builder instance
         * @see #objects()
         */
        public BUILDER addObject(Object object) {
            Objects.requireNonNull(object);
            this.objects.add(object);
            return identity();
        }

        /**
         * Level can be used directly (command line tools), mapped to Maven level (maven plugins),
         * or mapped to diagnostics kind (annotation processing).
         * <p>
         * Mapping table:
         * <table>
         *     <caption>Level mappings</caption>
         *     <tr>
         *         <th>Level</th>
         *         <th>Maven log level</th>
         *         <th>APT Diagnostic.Kind</th>
         *     </tr>
         *     <tr>
         *         <td>ERROR</td>
         *         <td>error</td>
         *         <td>ERROR</td>
         *     </tr>
         *     <tr>
         *         <td>WARNING</td>
         *         <td>warn</td>
         *         <td>WARNING</td>
         *     </tr>
         *     <tr>
         *         <td>INFO</td>
         *         <td>info</td>
         *         <td>NOTE</td>
         *     </tr>
         *     <tr>
         *         <td>DEBUG, TRACE</td>
         *         <td>debug</td>
         *         <td>N/A - only logged to logger</td>
         *     </tr>
         * </table>
         *
         * @return the level
         */
        public System.Logger.Level level() {
            return level;
        }

        /**
         * Message to be delivered to the user.
         *
         * @return the message
         */
        public Optional<String> message() {
            return Optional.ofNullable(message);
        }

        /**
         * Throwable if available.
         *
         * @return the throwable
         */
        public Optional<Throwable> throwable() {
            return Optional.ofNullable(throwable);
        }

        /**
         * Additional information, such as source elements.
         * These may or may not be ignored by the final log destination.
         * <p>
         * Expected supported types:
         * <ul>
         *     <li>APT: {@code Element}, {@code AnnotationMirror}, {@code AnnotationValue}</li>
         *     <li>Classpath scanning: {@code ClassInfo}, {@code MethodInfo} etc.</li>
         *     <li>Any environment: {@link io.helidon.common.types.TypeName},
         *     {@link io.helidon.common.types.TypeInfo},
         *     or {@link io.helidon.common.types.TypedElementInfo}</li>
         * </ul>
         *
         * @return the objects
         */
        public List<Object> objects() {
            return objects;
        }

        @Override
        public String toString() {
            return "CodegenEventBuilder{"
                    + "level=" + level + ","
                    + "message=" + message + ","
                    + "throwable=" + throwable + ","
                    + "objects=" + objects
                    + "}";
        }

        /**
         * Handles providers and decorators.
         */
        protected void preBuildPrototype() {
        }

        /**
         * Validates required properties.
         */
        protected void validatePrototype() {
            Errors.Collector collector = Errors.collector();
            if (message == null) {
                collector.fatal(getClass(), "Property \"message\" must not be null, but not set");
            }
            collector.collect().checkValid();
        }

        /**
         * Throwable if available.
         *
         * @param throwable throwable
         * @return updated builder instance
         * @see #throwable()
         */
        BUILDER throwable(Optional<? extends Throwable> throwable) {
            Objects.requireNonNull(throwable);
            this.throwable = throwable.map(Throwable.class::cast).orElse(this.throwable);
            return identity();
        }

        /**
         * Generated implementation of the prototype, can be extended by descendant prototype implementations.
         */
        protected static class CodegenEventImpl implements CodegenEvent {

            private final System.Logger.Level level;
            private final List<Object> objects;
            private final Optional<Throwable> throwable;
            private final String message;

            /**
             * Create an instance providing a builder.
             *
             * @param builder extending builder base of this prototype
             */
            protected CodegenEventImpl(BuilderBase<?> builder) {
                this.level = builder.level();
                this.message = builder.message().get();
                this.throwable = builder.throwable();
                this.objects = List.copyOf(builder.objects());
            }

            @Override
            public System.Logger.Level level() {
                return level;
            }

            @Override
            public String message() {
                return message;
            }

            @Override
            public Optional<Throwable> throwable() {
                return throwable;
            }

            @Override
            public List<Object> objects() {
                return objects;
            }

            @Override
            public String toString() {
                return "CodegenEvent{"
                        + "level=" + level + ","
                        + "message=" + message + ","
                        + "throwable=" + throwable + ","
                        + "objects=" + objects
                        + "}";
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof CodegenEvent other)) {
                    return false;
                }
                return Objects.equals(level, other.level())
                        && Objects.equals(message, other.message())
                        && Objects.equals(throwable, other.throwable())
                        && Objects.equals(objects, other.objects());
            }

            @Override
            public int hashCode() {
                return Objects.hash(level, message, throwable, objects);
            }

        }

    }

    /**
     * Fluent API builder for {@link io.helidon.codegen.CodegenEvent}.
     */
    class Builder extends BuilderBase<Builder> {

        private Builder() {
        }

        @Override
        public CodegenEvent build() {
            preBuildPrototype();
            validatePrototype();
            return new CodegenEventImpl(this);
        }

    }

}
