/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.WeightedBag;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoFileDescriptorSupplier;
import io.grpc.stub.ServerCalls;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * Encapsulates all metadata necessary to create and deploy a gRPC service.
 */
public class GrpcServiceDescriptor {
    /**
     * The {@link io.grpc.Context.Key} to use to obtain the {@link io.grpc.ServiceDescriptor}.
     */
    public static final Context.Key<GrpcServiceDescriptor> SERVICE_DESCRIPTOR_KEY =
            Context.key("Helidon.ServiceDescriptor");

    private final String name;
    private final String fullName;
    private final String packageName;
    private final Map<String, GrpcMethodDescriptor<?, ?>> methods;
    private final WeightedBag<ServerInterceptor> interceptors;
    private final Map<Context.Key<?>, Object> context;
    private final Descriptors.FileDescriptor proto;

    private GrpcServiceDescriptor(String name,
                                  Map<String, GrpcMethodDescriptor<?, ?>> methods,
                                  WeightedBag<ServerInterceptor> interceptors,
                                  Map<Context.Key<?>, Object> context,
                                  Descriptors.FileDescriptor proto) {
        String assignedName = Objects.requireNonNull(name);
        this.methods = methods;
        this.context = Collections.unmodifiableMap(context);
        this.interceptors = interceptors.copyMe();
        this.proto = proto;

        this.packageName = proto == null ? "" : proto.getPackage();
        String servicePrefix = packageName + (!packageName.isEmpty() ? "." : "");
        if (!servicePrefix.isEmpty() && assignedName.startsWith(servicePrefix)) {
            // If assignedName is already prefixed with package name, strip the package name part
            // so name is in simple format
            this.name = assignedName.replace(servicePrefix, "");
            // Use the assigned name as the fullName since it is already prefixed with the package name
            this.fullName = assignedName;
        } else {
            this.name = assignedName;
            this.fullName = servicePrefix + assignedName;
        }
    }

    /**
     * Return service name.
     *
     * @return service name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the service name prefixed with package directive if one exists.
     *
     * @return service name prefixed with package directive if one exists.
     */
    public String fullName() {
        return fullName;
    }

    /**
     * Returns package name from proto file.
     *
     * @return package name from proto file
     */
    public String packageName() {
        return packageName;
    }

    /**
     * Return {@link GrpcMethodDescriptor} for a specified method name.
     *
     * @param name method name
     * @return method descriptor for the specified name
     */
    public GrpcMethodDescriptor<?, ?> method(String name) {
        return methods.get(name);
    }

    /**
     * Return service methods.
     *
     * @return service methods
     */
    public Collection<GrpcMethodDescriptor<?, ?>> methods() {
        return Collections.unmodifiableCollection(methods.values());
    }

    /**
     * Return service interceptors.
     *
     * @return service interceptors
     */
    public WeightedBag<ServerInterceptor> interceptors() {
        return interceptors.readOnly();
    }

    /**
     * Return context map.
     *
     * @return context map
     */
    public Map<Context.Key<?>, Object> context() {
        return context;
    }

    /**
     * Return a proto file descriptor.
     *
     * @return a proto file descriptor
     */
    public Descriptors.FileDescriptor proto() {
        return proto;
    }

    @Override
    public String toString() {
        return "ServiceDescriptor(name='" + fullName + '\'' + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GrpcServiceDescriptor that = (GrpcServiceDescriptor) o;
        return fullName.equals(that.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName);
    }

    /**
     * Create a {@link Builder}.
     *
     * @param serviceClass the {@link Class} representing the service
     * @param name the name of the service
     * @return a {@link Builder}
     */
    public static Builder builder(Class<?> serviceClass, String name) {
        return new Builder(serviceClass, name);
    }

    /**
     * Create a {@link Builder}.
     *
     * @param service the {@link GrpcService} to use to initialise the builder
     * @return a {@link Builder}
     */
    public static Builder builder(GrpcService service) {
        return new Builder(service);
    }

    /**
     * Create a {@link Builder}.
     *
     * @param service the {@link BindableService} to use to initialise the builder
     * @return a {@link Builder}
     */
    public static Builder builder(BindableService service) {
        return new Builder(service);
    }

    // ---- inner interface: Config -----------------------------------------

    /**
     * Fluent configuration interface for the {@link GrpcServiceDescriptor}.
     */
    public interface Rules {
        /**
         * Set the name for the service.
         *
         * @param name the service name
         * @return this {@link Rules} instance for fluent call chaining
         * @throws NullPointerException if the name is null
         * @throws IllegalArgumentException if the name is a blank String
         */
        Rules name(String name);

        /**
         * Obtain the name fo the service this configuration configures.
         *
         * @return the name fo the service this configuration configures
         */
        String name();

        /**
         * Register the proto for the service.
         *
         * @param proto the service proto
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules proto(Descriptors.FileDescriptor proto);

        /**
         * Register the {@link MarshallerSupplier} for the service.
         *
         * @param marshallerSupplier the {@link MarshallerSupplier} for the service
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules marshallerSupplier(MarshallerSupplier marshallerSupplier);

        /**
         * Add one or more {@link ServerInterceptor} instances that will intercept calls
         * to this service.
         * <p>
         * If the added interceptors are annotated with the {@link io.helidon.common.Weight}
         * annotation then that value will be used to assign a weight to use when applying
         * the interceptor otherwise a priority of {@link InterceptorWeights#USER} will
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
         * @param priority the priority to assign to the interceptors
         * @param interceptors one or more {@link ServerInterceptor}s to add
         * @return this builder to allow fluent method chaining
         */
        Rules intercept(int priority, ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for a named method of the service.
         * <p>
         * If the added interceptors are annotated with the {@link io.helidon.common.Weight}
         * annotation then that value will be used to assign a weight to use when applying
         * the interceptor otherwise a priority of {@link InterceptorWeights#USER} will
         * be used.
         *
         * @param methodName the name of the method to intercept
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Rules intercept(String methodName, ServerInterceptor... interceptors);

        /**
         * Register one or more {@link io.grpc.ServerInterceptor interceptors} for a named method of the service.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param methodName the name of the method to intercept
         * @param priority the priority to assign to the interceptors
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Rules intercept(String methodName, int priority, ServerInterceptor... interceptors);

        /**
         * Add value to the {@link io.grpc.Context} for the service.
         *
         * @param key the key for the context value
         * @param value the value to add
         * @param <V> the type of the value
         * @return this {@link Rules} instance for fluent call chaining
         */
        <V> Rules addContextValue(Context.Key<V> key, V value);

        /**
         * Register unary method for the service.
         *
         * @param name the name of the method
         * @param method the unary method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method);

        /**
         * Register unary method for the service.
         *
         * @param name the name of the method
         * @param method the unary method to register
         * @param configurer the method configurer
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules unary(String name,
                                 ServerCalls.UnaryMethod<ReqT, ResT> method,
                                 GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register server streaming method for the service.
         *
         * @param name the name of the method
         * @param method the server streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method);

        /**
         * Register server streaming method for the service.
         *
         * @param name the name of the method
         * @param method the server streaming method to register
         * @param configurer the method configurer
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules serverStreaming(String name,
                                           ServerCalls.ServerStreamingMethod<ReqT, ResT> method,
                                           GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register client streaming method for the service.
         *
         * @param name the name of the method
         * @param method the client streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method);

        /**
         * Register client streaming method for the service.
         *
         * @param name the name of the method
         * @param method the client streaming method to register
         * @param configurer the method configurer
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules clientStreaming(String name,
                                           ServerCalls.ClientStreamingMethod<ReqT, ResT> method,
                                           GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name the name of the method
         * @param method the bi-directional streaming method to register
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name the name of the method
         * @param method the bi-directional streaming method to register
         * @param configurer the method configurer
         * @param <ReqT> the method request type
         * @param <ResT> the method response type
         * @return this {@link Rules} instance for fluent call chaining
         */
        <ReqT, ResT> Rules bidirectional(String name,
                                         ServerCalls.BidiStreamingMethod<ReqT, ResT> method,
                                         GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer);
    }

    // ---- inner class: Configurer -----------------------------------------

    /**
     * An interface implemented by classs that can configure
     * a {@link Rules}.
     */
    @FunctionalInterface
    public interface Configurer {
        /**
         * Apply extra configuration to a {@link Rules}.
         *
         * @param rules the {@link Rules} to configure
         */
        void configure(Rules rules);
    }

    // ---- inner class: Aware ----------------------------------------------

    /**
     * Allows users to specify that they would like to have access to a
     * {@link GrpcServiceDescriptor} within their {@link io.grpc.ServerInterceptor}
     * implementation.
     *
     * @deprecated Use the Helidon context to pass a descriptor instead
     * @see ContextSettingServerInterceptor
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    public interface Aware {
        /**
         * Set service descriptor.
         *
         * @param descriptor service descriptor instance
         */
        void setServiceDescriptor(GrpcServiceDescriptor descriptor);
    }

    // ---- inner class: Builder --------------------------------------------

    /**
     * A {@link GrpcServiceDescriptor} builder.
     */
    public static final class Builder implements Rules, io.helidon.common.Builder<Builder, GrpcServiceDescriptor> {
        private final Class<?> serviceClass;

        private String name;
        private Descriptors.FileDescriptor proto;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.create();
        private final Map<String, GrpcMethodDescriptor.Builder<?, ?>> methodBuilders = new LinkedHashMap<>();
        private final WeightedBag<ServerInterceptor> interceptors = WeightedBag.create(InterceptorWeights.USER);
        private final Map<Context.Key<?>, Object> context = new HashMap<>();

        Builder(Class<?> serviceClass, String name) {
            this.name = name == null || name.trim().isEmpty() ? serviceClass.getSimpleName() : name.trim();
            this.serviceClass = serviceClass;
        }

        Builder(GrpcService service) {
            this.name = service.serviceName();
            this.serviceClass = service.getClass();

            // TODO service.update(this);
        }

        @SuppressWarnings("unchecked")
        Builder(BindableService service) {
            ServerServiceDefinition def = service.bindService();

            this.name = def.getServiceDescriptor().getName();
            this.serviceClass = service.getClass();

            Object schemaDescriptor = def.getServiceDescriptor().getSchemaDescriptor();
            if (schemaDescriptor instanceof ProtoFileDescriptorSupplier) {
                this.proto = ((ProtoFileDescriptorSupplier) schemaDescriptor).getFileDescriptor();
            }

            for (ServerMethodDefinition<?, ?> smd : def.getMethods()) {
                MethodDescriptor<?, ?> md = smd.getMethodDescriptor();
                ServerCallHandler<?, ?> handler = smd.getServerCallHandler();
                String methodName = extractMethodName(md.getFullMethodName());
                GrpcMethodDescriptor.Builder<?, ?> descriptor = GrpcMethodDescriptor.builder(this.name, methodName,
                        (MethodDescriptor.Builder) md.toBuilder(), handler)
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
            for (Map.Entry<String, GrpcMethodDescriptor.Builder<?, ?>> entry : methodBuilders.entrySet()) {
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
                                          GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name,
                    MethodDescriptor.MethodType.UNARY,
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
                                                    GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer) {

            methodBuilders.put(name, createMethodDescriptor(name,
                    MethodDescriptor.MethodType.SERVER_STREAMING,
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
                                                    GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer) {

            methodBuilders.put(name, createMethodDescriptor(name,
                    MethodDescriptor.MethodType.CLIENT_STREAMING,
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
                                                  GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer) {

            methodBuilders.put(name, createMethodDescriptor(name,
                    MethodDescriptor.MethodType.BIDI_STREAMING,
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
            GrpcMethodDescriptor.Builder<?, ?> method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with name '" + methodName + "'");
            }

            method.intercept(interceptors);
            return this;
        }

        @Override
        public Builder intercept(String methodName, int priority, ServerInterceptor... interceptors) {
            GrpcMethodDescriptor.Builder<?, ?> method = methodBuilders.get(methodName);

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
        public GrpcServiceDescriptor build() {
            Map<String, GrpcMethodDescriptor<?, ?>> methods = new LinkedHashMap<>();
            String fullName = getFullName();
            for (Map.Entry<String, GrpcMethodDescriptor.Builder<?, ?>> entry : methodBuilders.entrySet()) {
                String methodName = entry.getKey();
                String fullMethodName = MethodDescriptor.generateFullMethodName(fullName, methodName);
                methods.put(methodName, entry.getValue().fullname(fullMethodName).build());
            }
            return new GrpcServiceDescriptor(name, methods, interceptors, context, proto);
        }

        @Override
        public String toString() {
            return "ServiceDescriptor.Builder(name='" + name + '\'' + ')';
        }

        // ---- helpers -----------------------------------------------------

        private <ReqT, ResT> GrpcMethodDescriptor.Builder<ReqT, ResT> createMethodDescriptor(
                String methodName,
                MethodDescriptor.MethodType methodType,
                ServerCallHandler<ReqT, ResT> callHandler,
                GrpcMethodDescriptor.Configurer<ReqT, ResT> configurer) {

            MethodDescriptor.Builder<ReqT, ResT> grpcDesc = MethodDescriptor.<ReqT, ResT>newBuilder()
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(getFullName(), methodName))
                    .setType(methodType)
                    .setSampledToLocalTracing(true);

            Class<ReqT> requestType = getTypeFromMethodDescriptor(methodName, true);
            Class<ResT> responseType = getTypeFromMethodDescriptor(methodName, false);

            GrpcMethodDescriptor.Builder<ReqT, ResT> builder =
                    GrpcMethodDescriptor.builder(this.name, methodName, grpcDesc, callHandler)
                    .defaultMarshallerSupplier(marshallerSupplier)
                    .requestType(requestType)
                    .responseType(responseType)
                    .fullname(getFullName());

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

            Descriptors.ServiceDescriptor svc = proto.findServiceByName(name);
            if (svc == null) {
                throw new IllegalArgumentException("Unable to find service " + name);
            }
            Descriptors.MethodDescriptor mtd = svc.findMethodByName(methodName);
            if (mtd == null) {
                throw new IllegalArgumentException("Unable to find method "
                                                   + methodName + " in service " + name);
            }
            Descriptors.Descriptor type = fInput ? mtd.getInputType() : mtd.getOutputType();

            String pkg = getPackageName();
            String outerClass = getOuterClassName();

            // make sure that any nested protobuf class names are converted
            // into a proper Java binary class name
            String className = pkg + "." + outerClass + type.getName();

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

        /**
         * Returns the service name prefixed with package directive if one exists.
         */
        private String getFullName() {
            String pkg = proto == null ? "" : proto.getPackage();
            String serviceName = name;
            if (!pkg.isEmpty() && !serviceName.startsWith(pkg + ".")) {
                serviceName = pkg + "." + serviceName;
            }
            return serviceName;
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
