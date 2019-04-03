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

package io.helidon.grpc.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.grpc.core.MarshallerSupplier;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.metrics.MetricType;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * Encapsulates all metadata necessary to create and deploy a gRPC service.
 *
 * @author Aleksandar Seovic  2019.03.18
 */
public class ServiceDescriptor {
    /**
     * The {@link io.grpc.Context.Key} to use to obtain the {@link io.grpc.ServiceDescriptor}.
     */
    public static final Context.Key<ServiceDescriptor> SERVICE_DESCRIPTOR_KEY =
            Context.key("Helidon.ServiceDescriptor");

    private final String name;
    private final Map<String, MethodDescriptor> methods;
    private final List<ServerInterceptor> interceptors;
    private final Map<Context.Key<?>, Object> context;
    private final MetricType metricType;
    private final HealthCheck healthCheck;

    private ServiceDescriptor(String name,
                              Map<String, MethodDescriptor> methods,
                              List<ServerInterceptor> interceptors,
                              Map<Context.Key<?>, Object> context,
                              MetricType metricType,
                              HealthCheck healthCheck) {
        this.name = name;
        this.methods = methods;
        this.interceptors = new ArrayList<>(interceptors);
        this.context = Collections.unmodifiableMap(context);
        this.metricType = metricType;
        this.healthCheck = healthCheck;
    }

    /**
     * Return service name.
     * @return service name
     */
    public String name() {
        return name;
    }

    /**
     * Return {@link io.helidon.grpc.server.MethodDescriptor} for a specified method name.
     *
     * @param name method name
     * @return method descriptor for the specified name
     */
    public MethodDescriptor method(String name) {
        return methods.get(name);
    }

    /**
     * Return service methods.
     * @return service methods
     */
    public Collection<MethodDescriptor> methods() {
        return Collections.unmodifiableCollection(methods.values());
    }

    /**
     * Return service interceptors.
     * @return service interceptors
     */
    public List<ServerInterceptor> interceptors() {
        return interceptors;
    }

    /**
     * Return context map.
     * @return context map
     */
    public Map<Context.Key<?>, Object> context() {
        return context;
    }

    /**
     * Return the type of metric that should be collected for this service.
     * @return metric type
     */
    public MetricType metricType() {
        return metricType;
    }

    /**
     * Return a {@link org.eclipse.microprofile.health.HealthCheck} for this service.
     * @return a health check
     */
    public HealthCheck healthCheck() {
        return healthCheck;
    }

    BindableService bindableService(List<ServerInterceptor> interceptors) {
        return new BindableServiceImpl(this, interceptors);
    }

    @Override
    public String toString() {
        return "ServiceDescriptor(name='" + name + '\'' + ')';
    }

    /**
     * Create a {@link Builder}.
     * @param service  the {@link GrpcService} to use to initialise the builder
     * @return a {@link Builder}
     */
    public static Builder builder(GrpcService service) {
        return new Builder(service);
    }

    /**
     * Create a {@link Builder}.
     * @param service  the {@link BindableService} to use to initialise the builder
     * @return a {@link Builder}
     */
    public static Builder builder(BindableService service) {
        return new Builder(service);
    }

    // ---- inner interface: Config -----------------------------------------

    /**
     * Fluent configuration interface for the {@link io.helidon.grpc.server.ServiceDescriptor}.
     */
    public interface Config {
        /**
         * Set the name for the service.
         *
         * @param name the service name
         * @return this {@link Config} instance for fluent call chaining
         * @throws java.lang.NullPointerException if the name is null
         * @throws java.lang.IllegalArgumentException if the name is a blank String
         */
        Config name(String name);

        /**
         * Obtain the name fo the service this configuration configures.
         * @return  the name fo the service this configuration configures
         */
        String name();

        /**
         * Register the proto for the service.
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
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for the service.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link Config} instance for fluent call chaining
         */
        Config intercept(ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for a named method of the service.
         *
         * @param methodName   the name of the method to intercept
         * @param interceptors the interceptor(s) to register
         *
         * @return this {@link Config} instance for fluent call chaining
         *
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Config intercept(String methodName, ServerInterceptor... interceptors);

        /**
         * Add value to the {@link io.grpc.Context} for the service.
         *
         * @param key   the key for the context value
         * @param value the value to add
         * @param <V>   the type of the value
         * @return this {@link Config} instance for fluent call chaining
         */
        <V> Config addContextValue(Context.Key<V> key, V value);

        /**
         * Register unary method for the service.
         *
         * @param name   the name of the method
         * @param method the unary method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method);

        /**
         * Register unary method for the service.
         *
         * @param name       the name of the method
         * @param method     the unary method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config unary(String name,
                                  ServerCalls.UnaryMethod<ReqT, ResT> method,
                                  Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer);

        /**
         * Register server streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the server streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method);

        /**
         * Register server streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the server streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config serverStreaming(String name,
                                            ServerCalls.ServerStreamingMethod<ReqT, ResT> method,
                                            Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer);

        /**
         * Register client streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the client streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method);

        /**
         * Register client streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the client streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config clientStreaming(String name,
                                            ServerCalls.ClientStreamingMethod<ReqT, ResT> method,
                                            Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the bi-directional streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the bi-directional streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link Config} instance for fluent call chaining
         */
        <ReqT, ResT> Config bidirectional(String name,
                                          ServerCalls.BidiStreamingMethod<ReqT, ResT> method,
                                          Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer);

        /**
         * Register the service {@link HealthCheck}.
         *
         * @param healthCheck the service {@link HealthCheck}
         * @return this {@link Config} instance for fluent call chaining
         */
        Config healthCheck(HealthCheck healthCheck);

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

    // ---- inner class: Aware ----------------------------------------------

    /**
     * Allows users to specify that they would like to have access to a
     * {@link io.helidon.grpc.server.ServiceDescriptor} within their {@link io.grpc.ServerInterceptor}
     * implementation.
     */
    public interface Aware {
        /**
         * Set service descriptor.
         * @param descriptor service descriptor instance
         */
        void setServiceDescriptor(ServiceDescriptor descriptor);
    }

    // ---- inner class: Builder --------------------------------------------

    /**
     * A {@link ServiceDescriptor} builder.
     */
    public static final class Builder implements Config, io.helidon.common.Builder<ServiceDescriptor> {
        private final Class<?> serviceClass;

        private String name;
        private Descriptors.FileDescriptor proto;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.defaultInstance();
        private Map<String, MethodDescriptor.Builder> methodBuilders = new LinkedHashMap<>();
        private List<ServerInterceptor> interceptors = new ArrayList<>();
        private Map<Context.Key<?>, Object> context = new HashMap<>();
        private MetricType metricType;
        private HealthCheck healthCheck;

        Builder(GrpcService service) {
            this.name         = service.name();
            this.serviceClass = service.getClass();
            this.healthCheck  = ConstantHealthCheck.up(name);

            service.update(this);
        }

        @SuppressWarnings("unchecked")
        Builder(BindableService service) {
            ServerServiceDefinition def = service.bindService();

            this.name         = def.getServiceDescriptor().getName();
            this.serviceClass = service.getClass();
            this.healthCheck  = ConstantHealthCheck.up(name);

            for (ServerMethodDefinition smd : def.getMethods()) {
                io.grpc.MethodDescriptor md      = smd.getMethodDescriptor();
                ServerCallHandler        handler = smd.getServerCallHandler();
                String                   methodName = extractMethodName(md.getFullMethodName());
                MethodDescriptor.Builder descriptor = MethodDescriptor.builder(methodName, md, handler);

                methodBuilders.put(methodName, descriptor);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Builder name(String name) {
            if (name == null) {
                throw new NullPointerException("name cannot be null");
            }

            if (name.trim().isEmpty()) {
                throw new IllegalArgumentException("name cannot be blank");
            }

            this.name = name.trim();
            for (Map.Entry<String, MethodDescriptor.Builder> entry : methodBuilders.entrySet()) {
                entry.getValue().fullname(name + "/" + entry.getKey());
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
        public <ReqT, ResT> Builder unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method) {
            return unary(name, method, null);
        }

        @Override
        public <ReqT, ResT> Builder unary(String name,
                                          ServerCalls.UnaryMethod<ReqT, ResT> method,
                                          Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name,
                                                            io.grpc.MethodDescriptor.MethodType.UNARY,
                                                            ServerCalls.asyncUnaryCall(method),
                                                            configurer));
            return this;
        }

        @Override
        public <ReqT, ResT> Builder serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method) {
            return serverStreaming(name, method, null);
        }

        @Override
        public <ReqT, ResT> Builder serverStreaming(String name,
                                                    ServerCalls.ServerStreamingMethod<ReqT, ResT> method,
                                                    Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name,
                                                            io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING,
                                                            ServerCalls.asyncServerStreamingCall(method),
                                                            configurer));
            return this;
        }

        @Override
        public <ReqT, ResT> Builder clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method) {
            return clientStreaming(name, method, null);
        }

        @Override
        public <ReqT, ResT> Builder clientStreaming(String name,
                                                    ServerCalls.ClientStreamingMethod<ReqT, ResT> method,
                                                    Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name,
                                                            io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING,
                                                            ServerCalls.asyncClientStreamingCall(method),
                                                            configurer));
            return this;
        }

        @Override
        public <ReqT, ResT> Builder bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method) {
            return bidirectional(name, method, null);
        }

        @Override
        public <ReqT, ResT> Builder bidirectional(String name,
                                                  ServerCalls.BidiStreamingMethod<ReqT, ResT> method,
                                                  Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name,
                                                            io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING,
                                                            ServerCalls.asyncBidiStreamingCall(method),
                                                            configurer));
            return this;
        }

        @Override
        public Builder intercept(ServerInterceptor... interceptors) {
            Collections.addAll(this.interceptors, interceptors);
            return this;
        }

        @Override
        public Builder intercept(String methodName, ServerInterceptor... interceptors) {
            MethodDescriptor.Builder method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with name '" + methodName + "'");
            }

            method.intercept(interceptors);

            return this;
        }

        @Override
        public <V> Builder addContextValue(Context.Key<V> key, V value) {
            context.put(key, value);
            return this;
        }

        @Override
        public Builder healthCheck(HealthCheck healthCheck) {
            this.healthCheck = healthCheck;
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
            return metricType(MetricType.TIMER);
        }

        @Override
        public Builder disableMetrics() {
            return metricType(MetricType.INVALID);
        }

        private Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        @Override
        public ServiceDescriptor build() {
            Map<String, MethodDescriptor> methods = new LinkedHashMap<>();

            for (Map.Entry<String, MethodDescriptor.Builder> entry : methodBuilders.entrySet()) {
                methods.put(entry.getKey(), entry.getValue().build());
            }

            return new ServiceDescriptor(name, methods, interceptors, context, metricType, healthCheck);
        }

        @Override
        public String toString() {
            return "ServiceDescriptor.Builder(name='" + name + '\'' + ')';
        }

        // ---- helpers -----------------------------------------------------

        @SuppressWarnings("unchecked")
        private <ReqT, ResT> MethodDescriptor.Builder<ReqT, ResT> createMethodDescriptor(
                String methodName,
                io.grpc.MethodDescriptor.MethodType methodType,
                ServerCallHandler<ReqT, ResT> callHandler,
                Consumer<MethodDescriptor.Config<ReqT, ResT>> configurer) {
            Class<ReqT> requestType = (Class<ReqT>) getTypeFromMethodDescriptor(methodName, true);
            Class<ResT> responseType = (Class<ResT>) getTypeFromMethodDescriptor(methodName, false);

            io.grpc.MethodDescriptor<ReqT, ResT> grpcDesc = io.grpc.MethodDescriptor.<ReqT, ResT>newBuilder()
                    .setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(this.name, methodName))
                    .setType(methodType)
                    .setRequestMarshaller(marshallerSupplier.get(requestType))
                    .setResponseMarshaller(marshallerSupplier.get(responseType))
                    .setSampledToLocalTracing(true)
                    .build();

            MethodDescriptor.Builder<ReqT, ResT> builder = MethodDescriptor.builder(methodName, grpcDesc, callHandler);
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
            // into a proper Java binary class name
            String className = pkg + "." + outerClass + type.getFullName().replace('.', '$');

            // the assumption here is that the protobuf generated classes can always
            // be loaded by the same class loader that loaded the service class,
            // as the service implementation is bound to depend on them
            try {
                return serviceClass.getClassLoader().loadClass(className);
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

            // append $ in order to timed a proper binary name for the nested message class
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
