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

package io.helidon.webserver.grpc.security;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.security.ClassToInstanceStore;

class GrpcSecurityConfigSupport {
    private GrpcSecurityConfigSupport() {
    }

    static class GrpcSecurityMethodCustomMethods {
        private GrpcSecurityMethodCustomMethods() {
        }

        /**
         * Register a custom object for security request(s).
         *
         * @param builder builder instance
         * @param object object expected by a security provider
         */
        @Prototype.BuilderMethod
        static void addObject(GrpcSecurityMethodConfig.BuilderBase<?, ?> builder, Object object) {
            Optional<ClassToInstanceStore<Object>> objects = builder.customObjects();

            if (objects.isEmpty()) {
                builder.customObjects(ClassToInstanceStore.create(object));
            } else {
                objects.get().putInstance(object);
            }
        }

        /**
         * Register a custom object for security request(s).
         *
         * @param builder builder instance
         * @param objectType type to use for the object
         * @param object object expected by a security provider
         */
        @Prototype.BuilderMethod
        static void addObject(GrpcSecurityMethodConfig.BuilderBase<?, ?> builder, Class<?> objectType, Object object) {
            Optional<ClassToInstanceStore<Object>> objects = builder.customObjects();
            ClassToInstanceStore<Object> store;

            if (objects.isEmpty()) {
                store = ClassToInstanceStore.create();
                builder.customObjects(store);
            } else {
                store = objects.get();
            }
            store.putInstance(objectType, object);
        }
    }

    static class GrpcSecurityHandlerCustomMethods {
        private GrpcSecurityHandlerCustomMethods() {
        }

        /**
         * Register a custom object for security request(s).
         *
         * @param builder builder instance
         * @param object object expected by a security provider
         */
        @Prototype.BuilderMethod
        static void addObject(GrpcSecurityHandlerConfig.BuilderBase<?, ?> builder, Object object) {
            Optional<ClassToInstanceStore<Object>> objects = builder.customObjects();

            if (objects.isEmpty()) {
                builder.customObjects(ClassToInstanceStore.create(object));
            } else {
                objects.get().putInstance(object);
            }
        }

        /**
         * Register a custom object for security request(s).
         *
         * @param builder builder instance
         * @param objectType type to use for the object
         * @param object object expected by a security provider
         */
        @Prototype.BuilderMethod
        static void addObject(GrpcSecurityHandlerConfig.BuilderBase<?, ?> builder, Class<?> objectType, Object object) {
            Optional<ClassToInstanceStore<Object>> objects = builder.customObjects();
            ClassToInstanceStore<Object> store;

            if (objects.isEmpty()) {
                store = ClassToInstanceStore.create();
                builder.customObjects(store);
            } else {
                store = objects.get();
            }
            store.putInstance(objectType, object);
        }
    }

    static class GrpcSecurityHandlerDecorator
            implements Prototype.BuilderDecorator<GrpcSecurityHandlerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(GrpcSecurityHandlerConfig.BuilderBase<?, ?> builder) {
            if (!builder.rolesAllowed().isEmpty()) {
                if (builder.authenticate().isEmpty()) {
                    builder.authenticate(true);
                }
                if (builder.authorize().isEmpty()) {
                    builder.authorize(true);
                }
            }

            if (builder.authenticationOptional().orElse(false) && builder.authenticate().isEmpty()) {
                builder.authenticate(true);
            }

            if (builder.authenticator().isPresent() && builder.authenticate().isEmpty()) {
                builder.authenticate(true);
            }

            if (builder.authorizer().isPresent() && builder.authorize().isEmpty()) {
                builder.authorize(true);
            }

            if (builder.auditEventType().isPresent() && builder.audit().isEmpty()) {
                builder.audit(true);
            }
            if (builder.auditMessageFormat().isPresent() && builder.audit().isEmpty()) {
                builder.audit(true);
            }
        }
    }
}
