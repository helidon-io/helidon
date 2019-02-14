package io.helidon.grpc.server;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;

import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;
import java.util.stream.Stream;


/**
 * Abstract base class for gRPC services.
 *
 * @author Aleksandar Seovic  2017.09.20
 */
public abstract class AbstractGrpcService
        implements BindableService
    {
    // ----- constructors ---------------------------------------------------

    public AbstractGrpcService()
        {
        this(new JavaMarshaller());
        }

    /**
     * Create a {@link AbstractGrpcService}.
     *
     * @param marshaller  the {@link Marshaller} to use to marshal
     *                    requests and responses
     */
    public AbstractGrpcService(Marshaller marshaller)
        {
        m_marshaller = marshaller;
        }


    // ----- BindableService methods ----------------------------------------

    public ServerServiceDefinition bindService()
        {
        m_builder = ServerServiceDefinition.builder(getServiceName(getClass()));

        defineMethods();

        return m_builder.build();
        }

    // ----- AbstractGrpcService methods ------------------------------------

    /**
     * Define the methods that this service implements.
     */
    protected void defineMethods()
        {
        java.lang.reflect.Method[] methods = getClass().getDeclaredMethods();
        Stream.of(methods)
                .filter(method -> method.isAnnotationPresent(RpcMethod.class))
                .forEach(this::addMethod);
        }

    private void addMethod(java.lang.reflect.Method method)
        {
        RpcMethod methodInfo = method.getAnnotation(RpcMethod.class);
        String name = "".equals(methodInfo.name()) ? method.getName() : methodInfo.name();
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != 2)
            {
            throw new IllegalArgumentException(String.format("gRPC method %s#%s must have exactly two arguments",
                                                             method.getDeclaringClass().getName(), method.getName()));
            }

        addMethod(name, methodInfo.type(), (request, response) ->
                {
                try
                    {
                    method.invoke(request, response);
                    }
                catch (Exception e)
                    {
                    throw new RuntimeException(e);
                    }
                });
        }

    /**
     * Add a method to this service.
     *
     * @param method  the {@link Method} to add
     */
    @SuppressWarnings("unchecked")
    protected <ReqT, ResT> void addMethod(String name, MethodType type, Method<ReqT, ResT> method)
        {
        MethodDescriptor<ReqT, ResT> descriptor = MethodDescriptor.<ReqT, ResT>newBuilder()
                .setType(type)
                .setFullMethodName(generateFullMethodName(this.getClass(), name))
                .setRequestMarshaller(m_marshaller)
                .setResponseMarshaller(m_marshaller)
                .build();

        m_builder.addMethod(descriptor, createCallHandler(type, method));
        }

    /**
     * Create a {@link ServerCallHandler} for the specified method.
     *
     * @param method  the {@link MethodDescriptor} describing the method
     *
     * @return  a {@link ServerCallHandler} for the specified method
     */
    private <ReqT, ResT> ServerCallHandler<ReqT, ResT> createCallHandler(MethodType type, Method<ReqT, ResT> method)
        {
        switch (type)
            {
            case UNARY:
            case SERVER_STREAMING:
                return ServerCalls.asyncUnaryCall(new ExecuteHandler<>(method));
            default:
                throw new IllegalArgumentException(String.format("Method type %s is not supported", type));
            }
        }

    /**
     * Generate a name for the request method.
     *
     * @param serviceClass  the {@link Class} of the service
     * @param name          the method name
     *
     * @return  a name for the request method
     */
    private String generateFullMethodName(Class<? extends AbstractGrpcService> serviceClass, String name)
        {
        return getServiceName(serviceClass) + "/" + name;
        }

    /**
     * Obtain the name of the service.
     *
     * @param serviceClass  the {@link Class} of the service
     *
     * @return  the name of the service
     */
    private String getServiceName(Class<? extends AbstractGrpcService> serviceClass)
        {
        RpcService service = serviceClass.getAnnotation(RpcService.class);
        String name = service == null || "".equals(service.name())
                      ? serviceClass.getName()
                      : service.name();
        int version = getServiceVersion(serviceClass);
        if (version < 0)
            {
            throw new IllegalStateException("Service version is not a positive integer");
            }
        return name + "/v" +
               version + '/' +
               "java"; // todo: add support for serializer name
        }

    /**
     * Obtain the version of the service.  This version must be a non-negative
     * value.
     *
     * @param serviceClass the {@link Class} of the service
     *
     * @return the version of the service
     */
    protected int getServiceVersion(Class<? extends AbstractGrpcService> serviceClass)
        {
        RpcService service = serviceClass.getAnnotation(RpcService.class);
        return service == null ? 0 : service.version();
        }

    // ----- inner class: ExecuteHandler ------------------------------------

    /**
     * A call handler.
     */
    private class ExecuteHandler<ReqT, ResT>
            implements ServerCalls.UnaryMethod<ReqT, ResT>,
                       ServerCalls.ServerStreamingMethod<ReqT, ResT>
        {
        /**
         * Create an {@link ExecuteHandler}.
         *
         * @param method  the method to execute
         */
        private ExecuteHandler(Method<ReqT, ResT> method)
            {
            this.method = method;
            }

        // ----- ServerCalls methods ----------------------------------------

        public void invoke(ReqT request, StreamObserver<ResT> observer)
            {
            try
                {
                method.execute(request, new ResponseObserver<>(observer));
                }
            catch (Throwable t)
                {
                Errors.handleError(t, observer, () -> "Error executing " + request);
                }
            }

        // ----- data members -----------------------------------------------

        /**
         * The service to use to execute the request.
         */
        private final Method<ReqT, ResT> method;
        }

    // ----- data members ---------------------------------------------------

    /**
     * The {@link Logger} to use.
     */
    private static final Logger LOGGER = Logger.getLogger(AbstractGrpcService.class.getCanonicalName());

    /**
     * The {@link Marshaller} for handling the request.
     */
    private Marshaller m_marshaller;

    /**
     * The builder containing methods registered with this service.
     */
    private ServerServiceDefinition.Builder m_builder;
    }
