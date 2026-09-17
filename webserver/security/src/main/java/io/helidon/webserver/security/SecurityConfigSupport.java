/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.buffers.Ascii;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.http.Method;
import io.helidon.security.ClassToInstanceStore;
import io.helidon.security.Security;

class SecurityConfigSupport {
    private SecurityConfigSupport() {
    }

    @SuppressWarnings("removal")
    static class SecurityFeatureConfigDecorator implements Prototype.BuilderDecorator<SecurityFeatureConfig.BuilderBase<?, ?>> {
        private static final System.Logger LOGGER = System.getLogger(SecurityFeatureConfig.class.getName());

        SecurityFeatureConfigDecorator() {
        }

        @Override
        public void decorate(SecurityFeatureConfig.BuilderBase<?, ?> target) {
            security(target);
            oldSetup(target);
        }

        private void oldSetup(SecurityFeatureConfig.BuilderBase<?, ?> target) {
            if (!target.paths().isEmpty()) {
                return;
            }
            var configOnBuilder = target.config();
            if (configOnBuilder.isPresent()) {
                var config = configOnBuilder.get().root().get("security.web-server");
                if (config.exists()) {
                    LOGGER.log(System.Logger.Level.WARNING, "Configuration key security.web-server is deprecated,"
                            + " please configure security integration with webserver under server.features.security instead");
                    target.config(config);
                }
            }
        }

        private void security(SecurityFeatureConfig.BuilderBase<?, ?> target) {
            Optional<Security> security = target.security();
            if (security.isPresent()) {
                return;
            }
            security = Contexts.context()
                    .orElseGet(Contexts::globalContext)
                    .get(Security.class);
            if (security.isPresent()) {
                target.security(security.get());
                return;
            }

            var config = target.config();
            if (config.isEmpty()) {
                throw new ConfigException("SecurityFeature requires either a configured Security, or security registered with"
                                                  + " global context, or configuration instance to construct security");
            }

            Security newSecurity = Security.create(config.get().root().get("security"));

            target.security(newSecurity);
        }
    }

    static class SecurityHandlerCustomMethods {
        private SecurityHandlerCustomMethods() {
        }

        /**
         * Register a custom object for security request(s).
         * This creates a hard dependency on a specific security provider, so use with care.
         *
         * @param builder builder instance
         * @param object  An object expected by security provider
         */
        @Prototype.BuilderMethod
        static void addObject(SecurityHandlerConfig.BuilderBase<?, ?> builder, Object object) {
            Optional<ClassToInstanceStore<Object>> objects = builder.customObjects();

            if (objects.isEmpty()) {
                builder.customObjects(ClassToInstanceStore.create(object));
            } else {
                objects.get().putInstance(object);
            }
        }

        /**
         * Register a custom object for security request(s).
         * This creates a hard dependency on a specific security provider, so use with care.
         *
         * @param builder builder instance
         * @param objectType type to register the object under
         * @param object  An object expected by security provider
         */
        @Prototype.BuilderMethod
        static void addObject(SecurityHandlerConfig.BuilderBase<?, ?> builder, Class<?> objectType, Object object) {
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

    static class SecurityHandlerDecorator implements Prototype.BuilderDecorator<SecurityHandlerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(SecurityHandlerConfig.BuilderBase<?, ?> builder) {
            // resolve implicit behavior

            // roles allowed implies atn and atz
            if (!builder.rolesAllowed().isEmpty()) {
                if (builder.authenticate().isEmpty()) {
                    builder.authenticate(true);
                }
                if (builder.authorize().isEmpty()) {
                    builder.authorize(true);
                }
            }

            // optional atn implies atn
            if (builder.authenticationOptional().orElse(false) && builder.authenticate().isEmpty()) {
                builder.authenticate(true);
            }

            // explicit atn provider implies atn
            if (builder.authenticator().isPresent() && builder.authenticate().isEmpty()) {
                builder.authenticate(true);
            }

            // explicit atz provider implies atz
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

    static class PathConfigCustomMethods {
        private static final System.Logger LOGGER = System.getLogger(PathsConfig.class.getName());
        private static final Set<Method> KNOWN_METHODS = Set.of(Method.GET,
                                                               Method.POST,
                                                               Method.QUERY,
                                                               Method.PUT,
                                                               Method.DELETE,
                                                               Method.HEAD,
                                                               Method.PATCH,
                                                               Method.OPTIONS,
                                                               Method.TRACE,
                                                               Method.CONNECT);

        private PathConfigCustomMethods() {
        }

        @Prototype.ConfigFactoryMethod("methods")
        static List<Method> createMethods(Config config) {
            Set<Method> methods = new LinkedHashSet<>();
            for (String name : config.asList(String.class).orElseThrow()) {
                Method method = Method.create(name);
                if (!methods.add(method)) {
                    continue;
                }
                Method uppercase = Method.create(Ascii.toUpperCase(name));
                if (!method.equals(uppercase) && KNOWN_METHODS.contains(uppercase)) {
                    methods.add(uppercase);
                    LOGGER.log(System.Logger.Level.WARNING,
                               "Security configuration " + config.key() + " contains HTTP method \"" + name
                                       + "\". For compatibility, both \"" + name + "\" and \"" + uppercase
                                       + "\" are matched. Automatic uppercasing will be removed in a future major version;"
                                       + " change the configured value to \"" + uppercase
                                       + "\", otherwise it will be matched case-sensitively.");
                }
            }
            return List.copyOf(methods);
        }

    }
}
