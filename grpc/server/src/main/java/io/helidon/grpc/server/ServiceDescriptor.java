/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.grpc.core.InterceptorPriorities;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.PriorityBag;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.stub.ServerCalls;
import org.eclipse.microprofile.health.HealthCheck;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * Encapsulates all metadata necessary to create and deploy a gRPC service.
 */
public class ServiceDescriptor {
    /**
     * The {@link io.grpc.Context.Key} to use to obtain the {@link io.grpc.ServiceDescriptor}.
     */
    public static final Context.Key<ServiceDescriptor> SERVICE_DESCRIPTOR_KEY =
            Context.key("Helidon.ServiceDescriptor");

    private final String name;
    private final Map<String, MethodDescriptor> methods;
    private final PriorityBag<ServerInterceptor> interceptors;
    private final Map<Context.Key<?>, Object> context;
    private final HealthCheck healthCheck;
    private final Descriptors.FileDescriptor proto;

    private ServiceDescriptor(String name,
                              Map<String, MethodDescriptor> methods,
                              PriorityBag<ServerInterceptor> interceptors,
                              Map<Context.Key<?>, Object> context,
                              HealthCheck healthCheck,
                              Descriptors.FileDescriptor proto) {
        this.name = Objects.requireNonNull(name);
        this.methods = methods;
        this.context = Collections.unmodifiableMap(context);
        this.healthCheck = healthCheck;
        this.interceptors = interceptors.copyMe();
        this.proto = proto;
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
    public PriorityBag<ServerInterceptor> interceptors() {
        return interceptors.readOnly();
    }

    /**
     * Return context map.
     * @return context map
     */
    public Map<Context.Key<?>, Object> context() {
        return context;
    }

    /**
     * Return a {@link org.eclipse.microprofile.health.HealthCheck} for this service.
     * @return a health check
     */
    public HealthCheck healthCheck() {
        return healthCheck;
    }

    /**
     * Return a proto file descriptor.
     * @return a proto file descriptor
     */
    public Descriptors.FileDescriptor proto() {
        return proto;
    }

    BindableService bindableService(PriorityBag<ServerInterceptor> interceptors) {
        return BindableServiceImpl.create(this, interceptors);
    }

    @Override
    public String toString() {
        return "ServiceDescriptor(name='" + name + '\'' + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceDescriptor that = (ServiceDescriptor) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Create a {@link Builder}.
     * @param serviceClass  the {@link Class} representing the service
     * @param name the name of the service
     * @return a {@link Builder}
     */
    public static Builder builder(Class serviceClass, String name) {
        return new Builder(serviceClass, name);
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
     * Fluent configuration interface for the {@link ServiceDescriptor}.
     */
    public interface Rules {
        /**
         * Set the name for the service.
         *
         * @param name the service name
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         * @throws java.lang.NullPointerException if the name is null
         * @throws java.lang.IllegalArgumentException if the name is a blank String
         */
        Rules name(String name);

        /**
         * Obtain the name fo the service this configuration configures.
         * @return  the name fo the service this configuration configures
         */
        String name();

        /**
         * Register the proto for the service.
         *
         * @param proto the service proto
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        Rules proto(Descriptors.FileDescriptor proto);

        /**
         * Register the {@link MarshallerSupplier} for the service.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        Rules marshallerSupplier(MarshallerSupplier marshallerSupplier);

        /**
         * Add one or more {@link ServerInterceptor} instances that will intercept calls
         * to this service.
         * <p>
         * If the added interceptors are annotated with the {@link javax.annotation.Priority}
         * annotation then that value will be used to assign a priority to use when applying
         * the interceptor otherwise a priority of {@link InterceptorPriorities#USER} will
         * be used.
         *
         * @param interceptors one or more {@link ServerInterceptor}s to add
         * @return this builder to allow fluent method chaining
         */
        Rules intercept(ServerInterceptor... interceptors);

        /**
         * Add one or more {@link ServerInterceptor} instances that will intercept calls
         * to this service.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param priority     the priority to assign to the interceptors
         * @param interceptors one or more {@link ServerInterceptor}s to add
         * @return this builder to allow fluent method chaining
         */
        Rules intercept(int priority, ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for a named method of the service.
         * <p>
         * If the added interceptors are annotated with the {@link javax.annotation.Priority}
         * annotation then that value will be used to assign a priority to use when applying
         * the interceptor otherwise a priority of {@link InterceptorPriorities#USER} will
         * be used.
         *
         * @param methodName   the name of the method to intercept
         * @param interceptors the interceptor(s) to register
         *
         * @return this {@link Rules} instance for fluent call chaining
         *
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Rules intercept(String methodName, ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for a named method of the service.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param methodName   the name of the method to intercept
         * @param priority     the priority to assign to the interceptors
         * @param interceptors the interceptor(s) to register
         *
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         *
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Rules intercept(String methodName, int priority, ServerInterceptor... interceptors);

        /**
         * Add value to the {@link io.grpc.Context} for the service.
         *
         * @param key   the key for the context value
         * @param value the value to add
         * @param <V>   the type of the value
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <V> Rules addContextValue(Context.Key<V> key, V value);

        /**
         * Register unary method for the service.
         *
         * @param name   the name of the method
         * @param method the unary method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method);

        /**
         * Register unary method for the service.
         *
         * @param name       the name of the method
         * @param method     the unary method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules unary(String name,
                                 ServerCalls.UnaryMethod<ReqT, ResT> method,
                                 MethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register server streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the server streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method);

        /**
         * Register server streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the server streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules serverStreaming(String name,
                                           ServerCalls.ServerStreamingMethod<ReqT, ResT> method,
                                           MethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register client streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the client streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method);

        /**
         * Register client streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the client streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules clientStreaming(String name,
                                           ServerCalls.ClientStreamingMethod<ReqT, ResT> method,
                                           MethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name   the name of the method
         * @param method the bi-directional streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name       the name of the method
         * @param method     the bi-directional streaming method to register
         * @param configurer the method configurer
         * @param <ReqT>     the method request type
         * @param <ResT>     the method response type
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules bidirectional(String name,
                                         ServerCalls.BidiStreamingMethod<ReqT, ResT> method,
                                         MethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register the service {@link HealthCheck}.
         *
         * @param healthCheck the service {@link HealthCheck}
         * @return this {@link io.helidon.grpc.server.ServiceDescriptor.Rules} instance for fluent call chaining
         */
        Rules healthCheck(HealthCheck healthCheck);
    }

    // ---- inner class: Configurer -----------------------------------------

    /**
     * An interface implemented by classs that can configure
     * a {@link ServiceDescriptor.Rules}.
     */
    @FunctionalInterface
    public interface Configurer {
        /**
         * Apply extra configuration to a {@link ServiceDescriptor.Rules}.
         *
         * @param rules the {@link ServiceDescriptor.Rules} to configure
         */
        void configure(Rules rules);
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
    public static final class Builder implements Rules, io.helidon.common.Builder<ServiceDescriptor> {
        private final Class<?> serviceClass;

        private String name;
        private Descriptors.FileDescriptor proto;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.defaultInstance();
        private Map<String, MethodDescriptor.Builder> methodBuilders = new LinkedHashMap<>();
        private PriorityBag<ServerInterceptor> interceptors = PriorityBag.withDefaultPriority(InterceptorPriorities.USER);
        private Map<Context.Key<?>, Object> context = new HashMap<>();
        private HealthCheck healthCheck;

        Builder(Class<?> serviceClass, String name) {
            this.name         = name == null || name.trim().isEmpty() ? serviceClass.getSimpleName() : name.trim();
            this.serviceClass = serviceClass;
            this.healthCheck  = ConstantHealthCheck.up(name);
        }

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

            Object schemaDescriptor = def.getServiceDescriptor().getSchemaDescriptor();
            if (schemaDescriptor instanceof ProtoFileDescriptorSupplier) {
                this.proto = ((ProtoFileDescriptorSupplier) schemaDescriptor).getFileDescriptor();
            }

            for (ServerMethodDefinition smd : def.getMethods()) {
                io.grpc.MethodDescriptor md = smd.getMethodDescriptor();
                ServerCallHandler        handler = smd.getServerCallHandler();
                String                   methodName = extractMethodName(md.getFullMethodName());
                MethodDescriptor.Builder descriptor = MethodDescriptor.builder(this.name, methodName, md.toBuilder(), handler)
                        .marshallerSupplier(marshallerSupplier);

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
                                          MethodDescriptor.Configurer<ReqT, ResT> configurer) {
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
                                                    MethodDescriptor.Configurer<ReqT, ResT> configurer) {

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
                                                    MethodDescriptor.Configurer<ReqT, ResT> configurer) {

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
                                                  MethodDescriptor.Configurer<ReqT, ResT> configurer) {

            methodBuilders.put(name, createMethodDescriptor(name,
                                                            io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING,
                                                            ServerCalls.asyncBidiStreamingCall(method),
                                                            configurer));
            return this;
        }

        @Override
        public Builder intercept(ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors));
            return this;
        }

        @Override
        public Builder intercept(int priority, ServerInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), priority);
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
        public Builder intercept(String methodName, int priority, ServerInterceptor... interceptors) {
            MethodDescriptor.Builder method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with name '" + methodName + "'");
            }

            method.intercept(priority, interceptors);

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
        public ServiceDescriptor build() {
            Map<String, MethodDescriptor> methods = new LinkedHashMap<>();

            for (Map.Entry<String, MethodDescriptor.Builder> entry : methodBuilders.entrySet()) {
                methods.put(entry.getKey(), entry.getValue().build());
            }

            return new ServiceDescriptor(name, methods, interceptors, context, healthCheck, proto);
        }

        @Override
        public String toString() {
            return "ServiceDescriptor.Builder(name='" + name + '\'' + ')';
        }

        // ---- helpers -----------------------------------------------------

        private <ReqT, ResT> MethodDescriptor.Builder<ReqT, ResT> createMethodDescriptor(
                String methodName,
                io.grpc.MethodDescriptor.MethodType methodType,
                ServerCallHandler<ReqT, ResT> callHandler,
                MethodDescriptor.Configurer<ReqT, ResT> configurer) {

            io.grpc.MethodDescriptor.Builder<ReqT, ResT> grpcDesc = io.grpc.MethodDescriptor.<ReqT, ResT>newBuilder()
                    .setFullMethodName(io.grpc.MethodDescriptor.generateFullMethodName(this.name, methodName))
                    .setType(methodType)
                    .setSampledToLocalTracing(true);

            Class<ReqT> requestType = getTypeFromMethodDescriptor(methodName, true);
            Class<ResT> responseType = getTypeFromMethodDescriptor(methodName, false);

            MethodDescriptor.Builder<ReqT, ResT> builder = MethodDescriptor.builder(this.name, methodName, grpcDesc, callHandler)
                    .defaultMarshallerSupplier(marshallerSupplier)
                    .requestType(requestType)
                    .responseType(responseType);

            if (configurer != null) {
                configurer.configure(builder);
            }

            return builder;
        }

        @SuppressWarnings("unchecked")
        private <T> Class<T> getTypeFromMethodDescriptor(String methodName, boolean fInput) {
            // if the proto is not present, assume that we are not using
            // protobuf for marshalling and that whichever marshaller is used
            // doesn't need type information (basically, that the serialized
            // stream is self-describing)
            if (proto == null) {
                return (Class<T>) Object.class;
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
                return (Class<T>) serviceClass.getClassLoader().loadClass(className);
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
