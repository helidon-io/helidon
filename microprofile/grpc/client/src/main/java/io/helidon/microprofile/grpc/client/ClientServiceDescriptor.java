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

package io.helidon.microprofile.grpc.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.grpc.core.InterceptorWeights;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.grpc.core.WeightedBag;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.BindableService;
import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServiceDescriptor;

import static io.helidon.grpc.core.GrpcHelper.extractMethodName;

/**
 * Encapsulates all details about a client side gRPC service.
 */
public class ClientServiceDescriptor {

    private final String serviceName;
    private final Map<String, ClientMethodDescriptor> methods;
    private final WeightedBag<ClientInterceptor> interceptors;
    private final CallCredentials callCredentials;

    private ClientServiceDescriptor(String serviceName,
                                    Map<String, ClientMethodDescriptor> methods,
                                    WeightedBag<ClientInterceptor> interceptors,
                                    CallCredentials callCredentials) {
        this.serviceName = serviceName;
        this.methods = methods;
        this.interceptors = interceptors;
        this.callCredentials = callCredentials;
    }

    /**
     * Create a {@link ClientServiceDescriptor} from a {@link ServiceDescriptor}.
     *
     * @param descriptor the {@link ServiceDescriptor}
     * @return a {@link ClientServiceDescriptor}
     */
    public static ClientServiceDescriptor create(ServiceDescriptor descriptor) {
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
     * Create a {@link Builder} from a {@link ServiceDescriptor}.
     *
     * @param service the {@link ServiceDescriptor}
     * @return a {@link Builder}
     */
    public static Builder builder(ServiceDescriptor service) {
        return new Builder(service);
    }

    /**
     * Create a {@link Builder} from a {@link BindableService}.
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
        try {
            Method method = serviceClass.getMethod("getServiceDescriptor");
            if (method.getReturnType() == ServiceDescriptor.class) {
                ServiceDescriptor svcDesc = (ServiceDescriptor) method.invoke(null);
                return builder(svcDesc);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException itEx) {
            // Ignored.
        }
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
     * Return {@link ClientMethodDescriptor} for a specified method getName.
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
    public WeightedBag<ClientInterceptor> interceptors() {
        return interceptors.readOnly();
    }

    /**
     * Return the {@link CallCredentials} set on this service.
     *
     * @return the {@link CallCredentials} set on this service
     */
    public CallCredentials callCredentials() {
        return this.callCredentials;
    }

    @Override
    public String toString() {
        return "ClientServiceDescriptor(name='" + serviceName + "')";
    }

    // ---- inner interface: Rules -----------------------------------------

    /**
     * Fluent configuration interface for the {@link ClientServiceDescriptor}.
     */
    public interface Rules {
        /**
         * Obtain the name fo the service this configuration configures.
         *
         * @return the name fo the service this configuration configures
         */
        String name();

        /**
         * Set the name for the service.
         *
         * @param name the name of service
         * @return this {@link Rules} instance for fluent call chaining
         * @throws NullPointerException     if the getName is null
         * @throws IllegalArgumentException if the getName is a blank String
         */
        Rules name(String name);

        /**
         * Register the proto file for the service.
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
         * Register one or more {@link ClientInterceptor interceptors} for the service.
         *
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules intercept(ClientInterceptor... interceptors);

        /**
         * Add one or more {@link ClientInterceptor} instances that will intercept calls
         * to this service.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param priority     the priority to assign to the interceptors
         * @param interceptors one or more {@link ClientInterceptor}s to add
         * @return this builder to allow fluent method chaining
         */
        Rules intercept(int priority, ClientInterceptor... interceptors);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for a named method of the service.
         *
         * @param methodName   the name of the method to intercept
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         * @throws IllegalArgumentException if no method exists for the specified getName
         */
        Rules intercept(String methodName, ClientInterceptor... interceptors);

        /**
         * Register one or more {@link ClientInterceptor interceptors} for a named method of the service.
         * <p>
         * The added interceptors will be applied using the specified priority.
         *
         * @param methodName   the name of the method to intercept
         * @param priority     the priority to assign to the interceptors
         * @param interceptors the interceptor(s) to register
         * @return this {@link Rules} instance for fluent call chaining
         *
         * @throws IllegalArgumentException if no method exists for the specified name
         */
        Rules intercept(String methodName, int priority, ClientInterceptor... interceptors);

        /**
         * Register unary method for the service.
         *
         * @param name The getName of the method
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules unary(String name);

        /**
         * Register unary method for the service.
         *
         * @param name       the name of the method
         * @param configurer the method configurer
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules unary(String name, Consumer<ClientMethodDescriptor.Rules> configurer);

        /**
         * Register server streaming method for the service.
         *
         * @param name The name of the method
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules serverStreaming(String name);

        /**
         * Register server streaming method for the service.
         *
         * @param name       the name of the method
         * @param configurer the method configurer
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules serverStreaming(String name, Consumer<ClientMethodDescriptor.Rules> configurer);

        /**
         * Register client streaming method for the service.
         *
         * @param name The name of the method
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules clientStreaming(String name);

        /**
         * Register client streaming method for the service.
         *
         * @param name       the name of the method
         * @param configurer the method configurer
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules clientStreaming(String name, Consumer<ClientMethodDescriptor.Rules> configurer);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name The name of the method
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules bidirectional(String name);

        /**
         * Register bi-directional streaming method for the service.
         *
         * @param name       the name of the method
         * @param configurer the method configurer
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules bidirectional(String name, Consumer<ClientMethodDescriptor.Rules> configurer);

        /**
         * Register the {@link CallCredentials} to be used for this service.
         *
         * @param callCredentials the {@link CallCredentials} to set.
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules callCredentials(CallCredentials callCredentials);

        /**
         * Register the {@link CallCredentials} to be used for the specified method in this service. This overrides
         * any {@link CallCredentials} set on this {@link ClientServiceDescriptor}
         *
         * @param name the method name
         * @param callCredentials the {@link CallCredentials} to set.
         * @return this {@link Rules} instance for fluent call chaining
         */
        Rules callCredentials(String name, CallCredentials callCredentials);

    }

    // ---- inner class: BaseBuilder --------------------------------------------

    /**
     * A {@link ClientServiceDescriptor} builder.
     */
    public static final class Builder implements Rules, io.helidon.common.Builder<Builder, ClientServiceDescriptor> {

        private String name;
        private final WeightedBag<ClientInterceptor> interceptors = WeightedBag.create(InterceptorWeights.USER);
        private final Class<?> serviceClass;
        private Descriptors.FileDescriptor proto;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.create();
        private CallCredentials callCredentials;

        private final Map<String, ClientMethodDescriptor.Builder> methodBuilders = new HashMap<>();

        /**
         * Builds the ClientService from a {@link BindableService}.
         *
         * @param service the {@link BindableService} to use to initialize the builder
         */
        private Builder(BindableService service) {
            this(service.bindService().getServiceDescriptor());
        }

        /**
         * Builds the ClientService from a {@link BindableService}.
         *
         * @param serviceDescriptor the {@link ServiceDescriptor} to use to initialize the builder
         */
        private Builder(ServiceDescriptor serviceDescriptor) {
            this.name = serviceDescriptor.getName();
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
        private Builder(String name, Class<?> serviceClass) {
            this.name = name;
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
        public Builder unary(String name, Consumer<ClientMethodDescriptor.Rules> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.UNARY, configurer));
            return this;
        }

        @Override
        public Builder serverStreaming(String name) {
            return serverStreaming(name, null);
        }

        @Override
        public Builder serverStreaming(String name,
                                       Consumer<ClientMethodDescriptor.Rules> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.SERVER_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder clientStreaming(String name) {
            return clientStreaming(name, null);
        }

        @Override
        public Builder clientStreaming(String name,
                                       Consumer<ClientMethodDescriptor.Rules> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.CLIENT_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder bidirectional(String name) {
            return bidirectional(name, null);
        }

        @Override
        public Builder bidirectional(String name,
                                     Consumer<ClientMethodDescriptor.Rules> configurer) {
            methodBuilders.put(name, createMethodDescriptor(name, MethodType.BIDI_STREAMING, configurer));
            return this;
        }

        @Override
        public Builder intercept(ClientInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors));
            return this;
        }

        @Override
        public Rules intercept(int priority, ClientInterceptor... interceptors) {
            this.interceptors.addAll(Arrays.asList(interceptors), priority);
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
        public Builder intercept(String methodName, int priority, ClientInterceptor... interceptors) {
            ClientMethodDescriptor.Builder method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with getName '" + methodName + "'");
            }

            method.intercept(priority, interceptors);

            return this;
        }

        @Override
        public Builder callCredentials(CallCredentials callCredentials) {
            this.callCredentials = callCredentials;
            return this;
        }

        @Override
        public Builder callCredentials(String methodName, CallCredentials callCredentials) {
            ClientMethodDescriptor.Builder method = methodBuilders.get(methodName);

            if (method == null) {
                throw new IllegalArgumentException("No method exists with getName '" + methodName + "'");
            }

            method.callCredentials(callCredentials);
            return this;
        }

        @Override
        public ClientServiceDescriptor build() {
            Map<String, ClientMethodDescriptor> methods = new LinkedHashMap<>();
            for (Map.Entry<String, ClientMethodDescriptor.Builder> entry : methodBuilders.entrySet()) {
                methods.put(entry.getKey(), entry.getValue().build());
            }

            return new ClientServiceDescriptor(name, methods, interceptors, callCredentials);
        }

        // ---- helpers -----------------------------------------------------

        private ClientMethodDescriptor.Builder createMethodDescriptor(
                String methodName,
                MethodType methodType,
                Consumer<ClientMethodDescriptor.Rules> configurer) {

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
