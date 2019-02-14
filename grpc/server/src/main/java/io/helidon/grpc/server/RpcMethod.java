package io.helidon.grpc.server;


import io.grpc.MethodDescriptor.MethodType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * @author Aleksandar Seovic  2019.01.30
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcMethod
    {
    String name() default "";

    MethodType type();
    }
