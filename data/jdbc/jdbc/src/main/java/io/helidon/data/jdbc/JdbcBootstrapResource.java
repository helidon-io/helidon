/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.data.jdbc;

import java.util.Objects;

import io.helidon.common.configurable.Resource;

/**
 * Associates one configured bootstrap resource with an environment-independent
 * diagnostic descriptor.
 * <p>
 * The wrapper deliberately does not expose a resource location through
 * {@link #toString()}. Bootstrap failures can therefore identify the script's
 * role, source category, and declaration order without retaining filesystem
 * paths, URI topology, configured SQL, or arbitrary stream descriptions.
 */
final class JdbcBootstrapResource {

    private final Descriptor descriptor;
    private final Resource resource;

    private JdbcBootstrapResource(Descriptor descriptor, Resource resource) {
        this.descriptor = descriptor;
        this.resource = resource;
    }

    /**
     * Creates a described bootstrap resource.
     *
     * @param role script role
     * @param position one-based position in bootstrap execution order
     * @param resource configured resource
     * @return described resource
     */
    static JdbcBootstrapResource create(Role role, int position, Resource resource) {
        Objects.requireNonNull(resource, "The bootstrap resource must not be null.");
        return new JdbcBootstrapResource(new Descriptor(role,
                                                        SourceType.create(resource.sourceType()),
                                                        position),
                                         resource);
    }

    @Override
    public String toString() {
        return descriptor.toString();
    }

    /**
     * Returns the safe descriptor.
     *
     * @return resource descriptor
     */
    Descriptor descriptor() {
        return descriptor;
    }

    /**
     * Returns the configured resource for provider-owned consumption.
     *
     * @return configured resource
     */
    Resource resource() {
        return resource;
    }

    /**
     * Bootstrap script role.
     */
    enum Role {

        /**
         * Drop script executed before initialization.
         */
        DROP("drop"),

        /**
         * Initialization script.
         */
        INIT("init");

        private final String text;

        Role(String text) {
            this.text = text;
        }

        /**
         * Returns the stable diagnostic text.
         *
         * @return role text
         */
        String text() {
            return text;
        }
    }

    /**
     * Bootstrap source category. Values intentionally describe only the
     * transport category and never its environment-specific location.
     */
    enum SourceType {

        /**
         * Classpath resource.
         */
        CLASSPATH("classpath"),

        /**
         * Filesystem resource.
         */
        FILE("file"),

        /**
         * URI-backed resource.
         */
        URI("URI"),

        /**
         * Plain configured text.
         */
        CONFIGURED_TEXT("configured text"),

        /**
         * Base64-configured binary content.
         */
        CONFIGURED_BINARY("configured binary"),

        /**
         * Programmatically supplied stream.
         */
        SUPPLIED_STREAM("supplied stream"),

        /**
         * Invalid or incomplete configured-resource definition.
         */
        UNSPECIFIED("configured");

        private final String text;

        SourceType(String text) {
            this.text = text;
        }

        /**
         * Converts the common resource source without consulting its location.
         *
         * @param source common resource source
         * @return bootstrap source category
         */
        static SourceType create(Resource.Source source) {
            return switch (source) {
            case CLASSPATH -> CLASSPATH;
            case FILE -> FILE;
            case URL -> URI;
            case CONTENT -> CONFIGURED_TEXT;
            case BINARY_CONTENT -> CONFIGURED_BINARY;
            case UNKNOWN -> SUPPLIED_STREAM;
            };
        }

        /**
         * Returns the stable diagnostic text.
         *
         * @return source text
         */
        String text() {
            return text;
        }
    }

    /**
     * Environment-independent identity of one bootstrap resource.
     *
     * @param role script role
     * @param sourceType source category
     * @param position one-based position in bootstrap execution order
     */
    record Descriptor(Role role, SourceType sourceType, int position) {

        Descriptor {
            Objects.requireNonNull(role, "The bootstrap resource role must not be null.");
            Objects.requireNonNull(sourceType, "The bootstrap resource source type must not be null.");
            if (position < 1) {
                throw new IllegalArgumentException("The bootstrap resource position must be positive.");
            }
        }

        @Override
        public String toString() {
            return sourceType.text() + " " + role.text() + " script";
        }
    }
}
