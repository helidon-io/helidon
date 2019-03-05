package io.helidon.grpc.server;


import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.helidon.grpc.server.GrpcServiceImpl.completeWithResult;
import static io.helidon.grpc.server.GrpcServiceImpl.completeWithoutResult;
import static io.helidon.grpc.server.GrpcServiceImpl.createSupplier;


/**
 * @author Aleksandar Seovic  2019.02.11
 */
public interface GrpcService
        extends BindableService
    {

    /**
     * Updates {@link Methods} with handlers representing this service.
     *
     * @param methods  methods to update
     */
    void update(Methods methods);

    default String name()
        {
        return getClass().getSimpleName();
        }

    default ServerServiceDefinition bindService()
        {
        return null;
        }

    // ---- convenience methods ---------------------------------------------

    default <T> void complete(StreamObserver<T> observer, T value)
        {
        observer.onNext(value);
        observer.onCompleted();
        }

    default <T> void complete(StreamObserver<T> observer, CompletionStage<T> future)
        {
        future.whenComplete(completeWithResult(observer));
        }

    default <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future)
        {
        future.whenCompleteAsync(completeWithResult(observer));
        }

    default <T> void completeAsync(StreamObserver<T> observer, CompletionStage<T> future, Executor executor)
        {
        future.whenCompleteAsync(completeWithResult(observer), executor);
        }

    default <T> void complete(StreamObserver<T> observer, Callable<T> callable)
        {
        try
            {
            observer.onNext(callable.call());
            observer.onCompleted();
            }
        catch (Throwable t)
            {
            observer.onError(t);
            }
        }

    default <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable)
        {
        completeAsync(observer, CompletableFuture.supplyAsync(createSupplier(callable)));
        }

    default <T> void completeAsync(StreamObserver<T> observer, Callable<T> callable, Executor executor)
        {
        completeAsync(observer, CompletableFuture.supplyAsync(createSupplier(callable), executor));
        }

    default <T> void complete(StreamObserver<T> observer, Runnable task, T result)
        {
        complete(observer, Executors.callable(task, result));
        }

    default <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result)
        {
        completeAsync(observer, Executors.callable(task, result));
        }

    default <T> void completeAsync(StreamObserver<T> observer, Runnable task, T result, Executor executor)
        {
        completeAsync(observer, Executors.callable(task, result), executor);
        }

    default <T> void stream(StreamObserver<T> observer, Stream<? extends T> stream)
        {
        stream.forEach(observer::onNext);
        observer.onCompleted();
        }

    default <T> void streamAsync(StreamObserver<T> observer, Stream<? extends T> stream, Executor executor)
        {
        executor.execute(() ->
            {
            stream.forEach(observer::onNext);
            observer.onCompleted();
            });
        }

    default <T> void stream(StreamObserver<T> observer, Supplier<Stream<? extends T>> streamSupplier)
        {
        streamSupplier.get().forEach(observer::onNext);
        observer.onCompleted();
        }

    default <T> void streamAsync(StreamObserver<T> observer, Supplier<Stream<? extends T>> streamSupplier, Executor executor)
        {
        executor.execute(() ->
            {
            streamSupplier.get().forEach(observer::onNext);
            observer.onCompleted();
            });
        }

    // todo: a bit of a chicken or egg when used with Coherence streaming methods, isn't it?
    default <T> Consumer<T> stream(StreamObserver<T> observer, CompletionStage<Void> future)
        {
        future.whenComplete(completeWithoutResult(observer));
        return observer::onNext;
        }

    // todo: ensure onNext is called on the executor thread for async overloads
    default <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> future)
        {
        future.whenCompleteAsync(completeWithoutResult(observer));
        return value -> CompletableFuture.runAsync(() -> observer.onNext(value));
        }

    default <T> Consumer<T> streamAsync(StreamObserver<T> observer, CompletionStage<Void> future, Executor executor)
        {
        future.whenCompleteAsync(completeWithoutResult(observer), executor);
        return value -> CompletableFuture.runAsync(() -> observer.onNext(value), executor);
        }

    // ---- Builder ---------------------------------------------------------

    /**
     * Creates new instance of {@link Builder service builder}.
     *
     * @return a new builder instance
     */
    static Builder builder(GrpcService service) {
        return new Builder(service);
    }

    final class ServiceConfig
        {
        private final BindableService service;

        private final List<ServerInterceptor> interceptors;

        public ServiceConfig(BindableService service)
            {
            this.service = service;
            this.interceptors = new ArrayList<>();
            }

        public BindableService service()
            {
            return service;
            }

        public List<ServerInterceptor> interceptors()
            {
            return interceptors;
            }

        public ServiceConfig intercept(ServerInterceptor interceptor)
            {
            interceptors.add(Objects.requireNonNull(interceptor));

            return this;
            }
        }

    /**
     *
     */
    interface Methods
        {
        Methods descriptor(FileDescriptor descriptor);
        Methods marshallerSupplier(MarshallerSupplier marshallerSupplier);

        <ReqT, ResT> Methods unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method);
        <ReqT, ResT> Methods serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method);
        <ReqT, ResT> Methods clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method);
        <ReqT, ResT> Methods bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method);
        }

    class Builder implements Methods, io.helidon.common.Builder<GrpcService>
        {
        private final GrpcService service;
        private final ServerServiceDefinition.Builder ssdBuilder;

        private FileDescriptor descriptor;
        private MarshallerSupplier marshallerSupplier = MarshallerSupplier.defaultInstance();

        Builder(GrpcService service)
            {
            this.service = service;
            this.ssdBuilder  = ServerServiceDefinition.builder(service.name());
            }

        // ---- Builder implementation --------------------------------------

        public GrpcService build()
            {
            service.update(this);
            return new GrpcServiceImpl(ssdBuilder.build());
            }

        // ---- Methods implementation --------------------------------------


        public Methods descriptor(FileDescriptor descriptor)
            {
            this.descriptor = descriptor;
            return this;
            }

        public Methods marshallerSupplier(MarshallerSupplier marshallerSupplier)
            {
            this.marshallerSupplier = marshallerSupplier;
            return this;
            }

        public <ReqT, ResT> Methods unary(String name, ServerCalls.UnaryMethod<ReqT, ResT> method)
            {
            ssdBuilder.addMethod(createMethodDescriptor(name, MethodType.UNARY),
                                 ServerCalls.asyncUnaryCall(method));
            return this;
            }

        public <ReqT, ResT> Methods serverStreaming(String name, ServerCalls.ServerStreamingMethod<ReqT, ResT> method)
            {
            ssdBuilder.addMethod(createMethodDescriptor(name, MethodType.SERVER_STREAMING),
                                 ServerCalls.asyncServerStreamingCall(method));
            return this;
            }

        public <ReqT, ResT> Methods clientStreaming(String name, ServerCalls.ClientStreamingMethod<ReqT, ResT> method)
            {
            ssdBuilder.addMethod(createMethodDescriptor(name, MethodType.CLIENT_STREAMING),
                                 ServerCalls.asyncClientStreamingCall(method));
            return this;
            }

        public <ReqT, ResT> Methods bidirectional(String name, ServerCalls.BidiStreamingMethod<ReqT, ResT> method)
            {
            ssdBuilder.addMethod(createMethodDescriptor(name, MethodType.BIDI_STREAMING),
                                 ServerCalls.asyncBidiStreamingCall(method));
            return this;
            }

        // ---- helpers -----------------------------------------------------

        @SuppressWarnings("unchecked")
        private <ReqT, ResT> MethodDescriptor<ReqT, ResT> createMethodDescriptor(String name, MethodType methodType)
            {
            Class<ReqT> requestType  = (Class<ReqT>) getTypeFromMethodDescriptor(name, true);
            Class<ResT> responseType = (Class<ResT>) getTypeFromMethodDescriptor(name, false);

            return MethodDescriptor.<ReqT, ResT>newBuilder()
                    .setFullMethodName(service.name() + "/" + name)
                    .setType(methodType)
                    .setRequestMarshaller(marshallerSupplier.get(requestType))
                    .setResponseMarshaller(marshallerSupplier.get(responseType))
                    .setSampledToLocalTracing(true)
                    .build();
            }

        private Class<?> getTypeFromMethodDescriptor(String methodName, boolean fInput)
            {
            // if the descriptor is not present, assume that we are not using
            // protobuf for marshalling and that whichever marshaller is used
            // doesn't need type information (basically, that the serialized
            // stream is self-describing)
            if (descriptor == null)
                {
                return Object.class;
                }

            // todo: add error handling here, and fail fast with a more
            // todo: meaningful exception (and message) than a NPE
            // todo: if the service or the method cannot be found
            Descriptors.ServiceDescriptor svc = descriptor.findServiceByName(service.name());
            Descriptors.MethodDescriptor mtd = svc.findMethodByName(methodName);
            Descriptors.Descriptor type = fInput ? mtd.getInputType() : mtd.getOutputType();

            String pkg = getPackageName();
            String outerClass = getOuterClassName();

            // make sure that any nested protobuf class names are converted
            // into a proper Java binary class name
            String className = pkg + "." +
                               outerClass +
                               type.getFullName().replace('.', '$');

            // the assumption here is that the protobuf generated classes can always
            // be loaded by the same class loader that loaded the service class,
            // as the service implementation is bound to depend on them
            try
                {
                return service.getClass().getClassLoader().loadClass(className);
                }
            catch (ClassNotFoundException e)
                {
                throw new RuntimeException(e);
                }
            }

        private String getPackageName()
            {
            String pkg = descriptor.getOptions().getJavaPackage();
            return "".equals(pkg) ? descriptor.getPackage() : pkg;
            }

        private String getOuterClassName()
            {
            DescriptorProtos.FileOptions options = descriptor.getOptions();
            if (options.getJavaMultipleFiles())
                {
                // there is no outer class -- each message will have its own top-level class
                return "";
                }

            String outerClass = options.getJavaOuterClassname();
            if ("".equals(outerClass))
                {
                outerClass = getOuterClassFromFileName(descriptor.getName());
                }

            // append $ in order to create a proper binary name for the nested message class
            return outerClass + "$";
            }

        private String getOuterClassFromFileName(String name)
            {
            // strip .proto extension
            name = name.substring(0, name.lastIndexOf(".proto"));

            String[]      words = name.split("_");
            StringBuilder sb    = new StringBuilder(name.length());

            for (String word : words)
                {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1));
                }

            return sb.toString();
            }
        }
    }
