/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * A JAX-RS application to be registered.
 * Can configure additional fields that are not part of JAX-RS configuration (all optional):
 * <ul>
 * <li>contextRoot - what is the base URI of this application (e.g. /myApp)</li>
 * <li>executorService - executor service to use when executing requests for this application
 * a single executor service can be shared between multiple applications</li>
 * </ul>
 */
public final class JaxRsApplication {
    private final String contextRoot;
    private final ResourceConfig config;
    private final ExecutorService executorService;
    private final String appName;
    private final String appClassName;
    private final String routingName;
    private final boolean routingNameRequired;
    private final Class<? extends Application> appClass;
    private final boolean synthetic;

    /**
     * Create a new instance based on an JAX-RS Application class.
     *
     * @param application class of an application (JAX-RS) to wrap
     * @return a new instance based on the application on root context path with no executor service (will use server default)
     */
    public static JaxRsApplication create(Class<? extends Application> application) {
        return builder()
                .application(application)
                .build();
    }

    /**
     * Create an instance based on a JAX-RS application.
     *
     * @param application JAX-RS application instance
     * @return a new instance based on the application on root context path with no executor service (will use server default)
     */
    public static JaxRsApplication create(Application application) {
        return builder()
                .application(application)
                .build();
    }

    /**
     * A new fluent API builder to create a customized {@link JaxRsApplication}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private JaxRsApplication(Builder builder) {
        this.contextRoot = builder.contextRoot;
        this.config = builder.config;
        this.executorService = builder.executorService;
        this.appClassName = builder.appClassName;
        this.routingName = builder.routingName;
        this.routingNameRequired = builder.routingNameRequired;
        this.appName = builder.appName;
        this.appClass = builder.appClass;
        this.synthetic = builder.synthetic;
    }

    @Override
    public String toString() {
        return "JAX-RS Application: " + appName;
    }

    Optional<String> contextRoot() {
        return Optional.ofNullable(contextRoot);
    }

    /**
     * Resource config of this application.
     *
     * @return config to register additional providers
     */
    public ResourceConfig resourceConfig() {
        return config;
    }

    /**
     * Indicates whether this app was identified as synthetic when it was built.
     *
     * @return true if synthetic, false otherwise
     */
    public boolean synthetic() {
        return synthetic;
    }

    Optional<ExecutorService> executorService() {
        return Optional.ofNullable(executorService);
    }

    String appClassName() {
        return appClassName;
    }

    Optional<String> routingName() {
        return Optional.ofNullable(routingName);
    }

    boolean routingNameRequired() {
        return routingNameRequired;
    }

    String appName() {
        return appName;
    }

    /**
     * Application class, if this application is based on an actual class.
     *
     * @return application class or empty optional if this is a synthetic application
     */
    public Optional<Class<? extends Application>> applicationClass() {
        return Optional.ofNullable(appClass);
    }

    /**
     * Fluent API builder to create {@link JaxRsApplication} instances.
     */
    public static class Builder {
        private Class<? extends Application> appClass;
        private String contextRoot;
        private ResourceConfig config;
        private ExecutorService executorService;
        private String appName;
        private String appClassName;
        private String routingName;
        private boolean routingNameRequired;
        private boolean synthetic = false;

        /**
         * Configure an explicit context root for this application.
         *
         * @param contextRoot context root to expose this application on, defaults to "/"
         * @return updated builder instance
         */
        public Builder contextRoot(String contextRoot) {
            this.contextRoot = normalize(contextRoot);
            return this;
        }

        /**
         * Set resource configuration that forms this application.
         * This will replace the current application (if any) with the specified config.
         *
         * @param config Jersey resource config to use
         * @return updated builder instance
         * @see #application(Application)
         * @see #application(Class)
         */
        public Builder config(ResourceConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Set the JAX-RS application that forms this instance.
         * This will replace the current application (if any) with the specified application.
         *
         * @param app JAX-RS application instance
         * @return updated builder instance
         * @see #application(Class)
         * @see #config(ResourceConfig)
         */
        public Builder application(Application app) {
            this.config = toConfig(app);

            Class<? extends Application> clazz = app.getClass();
            contextRoot(clazz);
            routingName(clazz);
            this.appClass = clazz;
            this.appClassName = clazz.getName();
            appNameUpdate(clazz.getSimpleName());

            return this;
        }

        /**
         * Set the JAX-RS application class that forms this instance.
         * This will replace the current application (if any) with the specified application.
         *
         * @param appClass JAX-RS application class
         * @return updated builder instance
         * @see #application(Application)
         * @see #config(ResourceConfig)
         */
        public Builder application(Class<? extends Application> appClass) {
            config = ResourceConfig.forApplicationClass(appClass);

            contextRoot(appClass);
            routingName(appClass);
            this.appClass = appClass;
            this.appClassName = appClass.getName();
            appNameUpdate(appClass.getSimpleName());

            return this;
        }

        /**
         * Configure an explicit application name.
         *
         * @param name name to use for this application, mostly for troubleshooting purposes
         * @return updated builder instance
         */
        public Builder appName(String name) {
            this.appName = name;
            return this;
        }

        /**
         * Configure an executor service to be used for this application.
         * Executor services can be shared between applications. If none is defined, server default will be used.
         *
         * @param executorService executor service to use with this application
         * @return updated builder instance
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Configure a routing name. This tells us this application should bind to a named port of the
         * web server.
         * To reset routing name to default, configure the {@link io.helidon.microprofile.server.RoutingName#DEFAULT_NAME}.
         *
         * @param routingName name to use
         * @return updated builder instance
         */
        public Builder routingName(String routingName) {
            if (routingName.equals(RoutingName.DEFAULT_NAME)) {
                this.routingName = null;
            } else {
                this.routingName = routingName;
            }
            return this;
        }

        /**
         * In case the {@link #routingName()} is configured to a non-default name, you can control with this property
         * whether the name is required (and boot would fail if such a named port is not configured), or default
         * routing is used when the named one is missing.
         *
         * @param routingNameRequired set to {@code true} if the named routing must be configured on web server, set to
         * {@code false} to use default routing if the named routing is missing
         * @return updated builder instance
         */
        public Builder routingNameRequired(boolean routingNameRequired) {
            this.routingNameRequired = routingNameRequired;
            return this;
        }

        Builder synthetic(boolean synthetic) {
            this.synthetic = synthetic;
            return this;
        }

        /**
         * Create a new instance based on this builder.
         *
         * @return application ready to be registered with {@link Server.Builder#addApplication(JaxRsApplication)}
         */
        public JaxRsApplication build() {
            if ((null == appName) && (null != appClassName)) {
                int lastDot = appClassName.lastIndexOf('.');
                if (lastDot > 0) {
                    appName = appClassName.substring(lastDot + 1);
                } else {
                    appName = appClassName;
                }
            }
            return new JaxRsApplication(this);
        }

        private void appNameUpdate(String newName) {
            if (null == this.appName) {
                this.appName = newName;
            }
        }

        private static ResourceConfig toConfig(Application application) {
            if (application instanceof ResourceConfig) {
                return (ResourceConfig) application;
            }

            return ResourceConfig.forApplication(application);
        }

        private void contextRoot(Class<?> clazz) {
            if (null != contextRoot) {
                return;
            }
            ApplicationPath path = clazz.getAnnotation(ApplicationPath.class);
            if (null != path) {
                contextRoot = normalize(path.value());
                return;
            }

            RoutingPath routingPath = clazz.getAnnotation(RoutingPath.class);
            if (null != routingPath) {
                contextRoot = routingPath.value();
            }
        }

        /**
         * Normalizes a context root by stripping off a trailing slash.
         *
         * @param contextRoot Context root to normalize.
         * @return Normalized context root.
         */
        private static String normalize(String contextRoot) {
            int length = contextRoot.length();
            return ((length > 1) && contextRoot.endsWith("/")) ? contextRoot.substring(0, length - 1) : contextRoot;
        }

        private void routingName(Class<?> clazz) {
            if (null != routingName) {
                return;
            }
            RoutingName rn = clazz.getAnnotation(RoutingName.class);
            if (null != rn) {
                this.routingName = rn.value();
                this.routingNameRequired = rn.required();
            }
        }

        /**
         * Configure an application class without inspecting it for annotations and
         * without creating a config from it.
         *
         * @param applicationClass class to use
         * @return updated builer instance
         */
        public Builder applicationClass(Class<? extends Application> applicationClass) {
            this.appClass = applicationClass;
            this.appClassName = applicationClass.getName();

            if (applicationClass != Application.class) {
                contextRoot(applicationClass);
                routingName(applicationClass);
            }
            return this;
        }
    }
}
