package io.helidon.grpc.server;


import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.inject.Named;
import javax.inject.Singleton;


/**
 * @author Aleksandar Seovic  2019.02.05
 */
@Singleton
@Named("java")
public class JavaMarshaller<T>
        implements MethodDescriptor.Marshaller<T>
    {
    public InputStream stream(T obj)
        {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(out))
            {
            oos.writeObject(obj);
            return new ByteArrayInputStream(out.toByteArray());
            }
        catch (Exception e)
            {
            throw new RuntimeException(e);
            }
        }

    @SuppressWarnings("unchecked")
    public T parse(InputStream in)
        {
        try (ObjectInputStream ois = new ObjectInputStream(in))
            {
            return (T) ois.readObject();
            }
        catch (Exception e)
            {
            throw new RuntimeException(e);
            }
        }
    }
