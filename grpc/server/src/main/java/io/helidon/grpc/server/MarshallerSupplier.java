package io.helidon.grpc.server;


import com.google.protobuf.MessageLite;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.lite.ProtoLiteUtils;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
@FunctionalInterface
public interface MarshallerSupplier
    {
    <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz);

    static MarshallerSupplier defaultInstance()
        {
        return new DefaultMarshallerSupplier();
        }

    class DefaultMarshallerSupplier
            implements MarshallerSupplier
        {
        private static MethodDescriptor.Marshaller JAVA_MARSHALLER = new JavaMarshaller();

        @SuppressWarnings("unchecked")
        public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz)
            {
            if (MessageLite.class.isAssignableFrom(clazz))
                {
                try
                    {
                    java.lang.reflect.Method getDefaultInstance = clazz.getDeclaredMethod("getDefaultInstance");
                    MessageLite instance = (MessageLite) getDefaultInstance.invoke(clazz);
                    return (MethodDescriptor.Marshaller<T>) ProtoLiteUtils.marshaller(instance);
                    }
                catch (Exception e)
                    {
                    String msg = String.format("Attempting to use class %s, which is not a valid Protobuf message, with a default marshaller", clazz.getName());
                    throw new IllegalArgumentException(msg);
                    }
                }

            return JAVA_MARSHALLER;
            }
        }
    }
