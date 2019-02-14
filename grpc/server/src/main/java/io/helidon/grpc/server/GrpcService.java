package io.helidon.grpc.server;


import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;


/**
 * @author Aleksandar Seovic  2019.02.11
 */
public interface GrpcService
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

    /**
     * Creates new instance of {@link Builder service builder}.
     *
     * @return a new builder instance
     */
    static Builder builder(GrpcService service) {
        return new Builder(service);
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

    class Builder implements Methods, io.helidon.common.Builder<BindableService>
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

        public BindableService build()
            {
            service.update(this);
            return new BindableServiceImpl(ssdBuilder.build());
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
