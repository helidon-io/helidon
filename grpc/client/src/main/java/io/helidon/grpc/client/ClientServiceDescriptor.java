/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.grpc.core.MarshallerSupplier;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServiceDescriptor;
import org.eclipse.microprofile.metrics.MetricType;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * Encapsulates all details about a client side gRPC service.
 *
 * @author Mahesh Kannan
 */
public class ClientServiceDescriptor {

    private String serviceName;
    private Map<String, ClientMethodDescriptor> methods;
    private LinkedList<ClientInterceptor> interceptors;
    private MetricType metricType;

    private ClientServiceDescriptor(String serviceName,
                                    Map<String, ClientMethodDescriptor> methods,
                                    LinkedList<ClientInterceptor> interceptors,
                                    MetricType metricType) {
        this.serviceName = serviceName;
        this.methods = methods;
        this.interceptors = interceptors;
        this.metricType = metricType;
    }

    /**
     * Create a {@link ClientServiceDescriptor} from a {@link io.grpc.ServiceDescriptor}.
     *
     * @param descriptor the {@link io.grpc.ServiceDescriptor}
     * @return a {@link ClientServiceDescriptor}
     */
    public static ClientServiceDescriptor create(io.grpc.ServiceDescriptor descriptor) {
        return builder(descriptor).build();
    }

    /**
     * Create a {@link ClientServiceDescriptor} from a {@link BindableService}.
     *
     * @param service the BindableService
     * @return a {@link ClientServiceDescriptor}
     */
    public static ClientServiceDescriptor create(BindableService service) {
        return builder(service).build();
    }

    /**
     * Create a {@link ClientServiceDescriptor.Builder} from a {@link io.grpc.ServiceDescriptor}.
     *
     * @param service the {@link io.grpc.ServiceDescriptor}
     * @return a {@link Builder}
     */
    public static Builder builder(io.grpc.ServiceDescriptor service) {
        return new Builder(service);
    }

    /**
     * Create a {@link ClientServiceDescriptor.Builder} from a {@link BindableService}.
     *
     * @param service the {@link BindableService}
     * @return a {@link Builder}
     */
    public static Builder builder(BindableService service) {
        return new Builder(service);
    }

    /**
     * Create a {@link Builder} form a name and type.
     * <p>
     * The {@link Class#getSimpleName() class simple name} will be used for the service name.
     *
     * @param serviceClass the service class
     * @return a {@link Builder}
     */
    public static Builder builder(Class<?> serviceClass) {
        return builder(serviceClass.getSimpleName(), serviceClass);
    }

    /**
     * Create a {@link Builder} form a name and type.
     *
     * @param serviceName  the getName of the service to use to initialise the builder
     * @param serviceClass the service class
     * @return a {@link Builder}
     */
    public static Builder builder(String serviceName, Class<?> serviceClass) {
        return new Builder(serviceName, serviceClass);
    }

    /**
     * Obtain the service name.
     *
     * @return the service name
     */
    public String name() {
        return serviceName;
    }

    /**
     * Return {@link io.helidon.grpc.client.ClientMethodDescriptor} for a specified method getName.
     *
     * @param name   method getName
     * @return method getDescriptor for the specified getName
     */
    public ClientMethodDescriptor method(String name) {
        return methods.get(name);
    }

    /**
     * Return the collections of methods that make up this service.
     *
     * @return service methods
     */
    public Collection<ClientMethodDescriptor> methods() {
        return Collections.unmodifiableCollection(methods.values());
    }

    /**
     * Return service interceptors.
     *
     * @return service interceptors
     */
    public List<ClientInterceptor> interceptors() {
        return interceptors;
    }

    /**
     * Return the type of metric that should be collected for this service.
     *
     * @return metric type
     */
    public MetricType metricType() {
        return metricType;
    }

    @Override
    public String toString() {
        return "ClientServiceDescriptor(name='" + serviceName + "')";
    }

    // ---- inner interface: Config -----------------------------------------

    /**
     * Fluent configuration interface for the {@link ClientServiceDescriptor}.
     */
    public interface Config {
        /**
         * Obtain the name fo the service this configuration configures.
         *
         * @return  the name fo the service this configuration configures
         */
        String name();

        /**
         * Set the name for the service.
         *
         * @param name the name of service
         * @return this {@link Config} instance for fluent call chaining
         * @throws NullPointerException     if the getName is null
         * @throws IllegalArgumentException if the getName is a blank String
         */
        Config name(String name);

        /**
         * Register the proto file for the service.
         *
         * @param proto the service proto
         * @return this {@link Config} instance for fluent call chaining
         */
        Config proto(Descriptors.FileDescriptor proto);

        /**
         * Register the {@link MarshallerSupplier} for the service.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link Config} instance for fluent call chaining
         */
        Config marshallerSupplier(MarshallerSupplier marshallerSupplier);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for the service.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link Config} instance for fluent call chaining
         */
        Config intercept(ClientInterceptor... interceptors);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for a named method of the service.
         *
         * @param methodName   the getName of the method to intercept
         * @param interceptors the interceptor(s) to register
         * @return this {@link Config} instance for fluent call chaining
         * @throws IllegalArgumentException if no method exists for the specified getName
         */
        Config intercept(String methodName, ClientInterceptor... interceptors);

        /**
         * Register unary method for the service.
         *
         * @param name The getName of the method
         * @return this {@link Config} instance for fluent call chaining
         */
        Config unary(String name);

        /**
         * Register unary method for the service.
         *
         * @param name       the getName of the method
         * @param configurer the method configurer
         * @return this {@link Config} instance for fluent call chaining
         */
        Config unary(String name, Consumer<ClientMethodDescriptor.Config> configurer);

        /**
         * Register server streaming method for the service.
         *
         * @param name The getName of the method
         * @return this {@link Config} instance for fluent call chaining
         */
        Config serverStreaming(String name);

        /**
         * Register server streaming method for the service.
         *
         * @param name       the getName of the method
         * @param configurer the method configurer
         * @return this {@link Config} instance for fluent call chaining
         */
        Config serverStreaming(String name, Consumer<ClientMethodDescriptor.Config> configurer);

        /**
         * Register client streaming method for the service.
         *
         * @param name The getName of the method
         * @return this {@link Config} instance for fluent call chaining
         */
        Config clientStreaming(String name);

        /**
         * Register client streaming method for the service.
         *
         * @param name       the getName of the method
         * @param configurer the method configurer
         * @return this {@link Config} instance for fluent call chaining
         */
        Config clientStreaming(String name, Consumer<ClientMethodDescriptor.Config> configurer);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name The getName of the method
         * @return this {@link Config} instance for fluent call chaining
         */
        Config bidirectional(String name);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name       the getName of the method
         * @param configurer the method configurer
         * @return this {@link Config} instance for fluent call chaining
         */
        Config bidirectional(String name, Consumer<ClientMethodDescriptor.Config> configurer);

        /**
         * Collect metrics for this service using {@link org.eclipse.microprofile.metrics.Counter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config counted();

        /**
         * Collect metrics for this service using {@link org.eclipse.microprofile.metrics.Meter}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config metered();

        /**
         * Collect metrics for this service using {@link org.eclipse.microprofile.metrics.Histogram}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config histogram();

        /**
         * Collect metrics for this service using {@link org.eclipse.microprofile.metrics.Timer}.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config timed();

        /**
         * Explicitly disable metrics collection for this service.
         *
         * @return this {@link Config} instance for fluent call chaining
         */
        Config disableMetrics();
    }

    // ---- inner class: BaseBuilder --------------------------------------------

    /**
     * A {@link ClientServiceDescriptor} builder.
     */
    public static final class Builder
            implements Config, io.helidon.common.Builder<ClientServiceDescriptor> {
        private String name;
        private LinkedList<ClientInterceptor> interceptors = new LinkedList<>();
        private MetricType metricType;
        private Class<?> serviceClass;
        private Descriptors.FileDescriptor proto;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.defaultInstance();

        private Map<String, ClientMethodDescriptor.Builder> methodBuilders = new HashMap<>();

        /**
         * Builds the ClientService from a {@link io.grpc.BindableService}.
         *
         * @param service the {@link io.grpc.BindableService} to use to initialize the builder
         */
        private Builder(BindableService service) {
            this(service.bindService().getServiceDescriptor());
        }

        /**
         * Builds the ClientService from a {@link io.grpc.BindableService}.
         *
         * @param serviceDescriptor the {@link io.grpc.ServiceDescriptor} to use to initialize the builder
         */
        private Builder(ServiceDescriptor serviceDescriptor) {
            this.name         = serviceDescriptor.getName();
            this.serviceClass = serviceDescriptor.getClass();

            for (io.grpc.MethodDescriptor<?, ?> md : serviceDescriptor.getMethods()) {
                String methodName = extractMethodName(md.getFullMethodName());

                methodBuilders.put(methodName, ClientMethodDescriptor.builder(this.name, methodName, md.toBuilder()));
            }
        }

        /**
         * Create a new {@link Builder}.
         *
         * @param name the service name
         * @param serviceClass the service class
         */
        private Builder(String name, Class serviceClass) {
            this.name         = name;
            this.serviceClass = serviceClass;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Builder name(String serviceName) {
            if (serviceName == null) {
                throw new NullPointerException("Service getName cannot be null");
            }

            if (serviceName.trim().isEmpty()) {
                throw new IllegalArgumentException("Service getName cannot be blank");
            }

            this.name = serviceName.trim();
            for (Map.Entry<String, ClientMethodDescriptor.Builder> e : methodBuilders.entrySet()) {
                e.getValue().fullName(io.grpc.MethodDescriptor.generateFullMethodName(this.name, e.getKey()));
            }
            return this;
        }

        @Override
        public Builder proto(Descriptors.FileDescriptor proto) {
            this.proto = proto;
            return this;
        }

        @Override
        public Builder marshallerSupplier(MarshallerSupplier marshallerSupplier) {
            this.marshallerSupplier = marshallerSupplier;
            return this;
        }

        @Override
        public Builder unary(String name) {
            return unary(name, null);
        }

        @Override
        public Builder unary(String name, Consumer<ClientMethodDescriptor.Config> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.UNARY, configurer));
            return this;
        }

        @Override
        public Builder serverStreaming(String name) {
            return serverStreaming(name, null);
        }

        @Override
        public Builder serverStreaming(String name,
                                                    Consumer<ClientMethodDescriptor.Config> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.SERVER_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder clientStreaming(String name) {
            return clientStreaming(name, null);
        }

        @Override
        public Builder clientStreaming(String name,
                                                    Consumer<ClientMethodDescriptor.Config> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.CLIENT_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder bidirectional(String name) {
            return bidirectional(name, null);
        }

        @Override
        public Builder bidirectional(String name,
                                                  Consumer<ClientMethodDescriptor.Config> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.BIDI_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder intercept(ClientInterceptor... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            System.out.println("Added interceptor; Count: " + this.interceptors.size());
            return this;
        }

        @Override
        public Builder intercept(String methodName, ClientInterceptor... interceptors) {
            ClientMethodDescriptor.Builder method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with getName '" + methodName + "'");
            }

            method.intercept(interceptors);

            return this;
        }

        @Override
        public Builder counted() {
            return metricType(MetricType.COUNTER);
        }

        @Override
        public Builder metered() {
            return metricType(MetricType.METERED);
        }

        @Override
        public Builder histogram() {
            return metricType(MetricType.HISTOGRAM);
        }

        @Override
        public Builder timed() {
            this.metricType = MetricType.TIMER;
            return this;
        }

        @Override
        public Builder disableMetrics() {
            return metricType(MetricType.INVALID);
        }

        @Override
        public ClientServiceDescriptor build() {
            Map<String, ClientMethodDescriptor> methods = new LinkedHashMap<>();
            for (Map.Entry<String, ClientMethodDescriptor.Builder> entry : methodBuilders.entrySet()) {
                methods.put(entry.getKey(), entry.getValue().build());
            }

            return new ClientServiceDescriptor(name,
                                               methods,
                                               interceptors,
                                               metricType);
        }

        // ---- helpers -----------------------------------------------------

        private Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        private ClientMethodDescriptor.Builder createMethodDescriptor(
                String methodName,
                MethodType methodType,
                Consumer<ClientMethodDescriptor.Config> configurer) {

            io.grpc.MethodDescriptor.Builder<?, ?> grpcDesc = io.grpc.MethodDescriptor.newBuilder()
                    .setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(this.name, methodName))
                    .setType(methodType)
                    .setSampledToLocalTracing(true);

            Class<?> requestType = getTypeFromMethodDescriptor(methodName, true);
            Class<?> responseType = getTypeFromMethodDescriptor(methodName, false);

            ClientMethodDescriptor.Builder builder = ClientMethodDescriptor.builder(this.name, methodName, grpcDesc)
                    .defaultMarshallerSupplier(this.marshallerSupplier)
                    .requestType(requestType)
                    .responseType(responseType);

            if (configurer != null) {
                configurer.accept(builder);
            }

            return builder;
        }

        private Class<?> getTypeFromMethodDescriptor(String methodName, boolean fInput) {

            // if the proto is not present, assume that we are not using
            // protobuf for marshalling and that whichever marshaller is used
            // doesn't need type information (basically, that the serialized
            // stream is self-describing)
            if (proto == null) {
                return Object.class;
            }

            // todo: add error handling here, and fail fast with a more
            // todo: meaningful exception (and message) than a NPE
            // todo: if the service or the method cannot be found
            Descriptors.ServiceDescriptor svc = proto.findServiceByName(name);
            Descriptors.MethodDescriptor mtd = svc.findMethodByName(methodName);
            Descriptors.Descriptor type = fInput ? mtd.getInputType() : mtd.getOutputType();

            String pkg = getPackageName();
            String outerClass = getOuterClassName();

            // make sure that any nested protobuf class names are converted
            // into a proper Java binary class getName
            String className = pkg + "." + outerClass + type.getFullName().replace('.', '$');

            // the assumption here is that the protobuf generated classes can always
            // be loaded by the same class loader that loaded the service class,
            // as the service implementation is bound to depend on them
            try {
                return serviceClass != null
                        ? serviceClass.getClassLoader().loadClass(className)
                        : this.getClass().getClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        private String getPackageName() {
            String pkg = proto.getOptions().getJavaPackage();
            return "".equals(pkg) ? proto.getPackage() : pkg;
        }

        private String getOuterClassName() {
            DescriptorProtos.FileOptions options = proto.getOptions();
            if (options.getJavaMultipleFiles()) {
                // there is no outer class -- each message will have its own top-level class
                return "";
            }

            String outerClass = options.getJavaOuterClassname();
            if ("".equals(outerClass)) {
                outerClass = getOuterClassFromFileName(proto.getName());
            }

            // append $ in order to timed a proper binary getName for the nested message class
            return outerClass + "$";
        }

        private String getOuterClassFromFileName(String name) {
            // strip .proto extension
            name = name.substring(0, name.lastIndexOf(".proto"));

            String[] words = name.split("_");
            StringBuilder sb = new StringBuilder(name.length());

            for (String word : words) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));
            }

            return sb.toString();
        }
    }
}
