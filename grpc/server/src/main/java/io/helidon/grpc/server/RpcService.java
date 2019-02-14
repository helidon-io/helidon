package io.helidon.grpc.server;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * @author Aleksandar Seovic  2019.01.30
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService
    {
    String name() default "";
    
    int version() default 0;
    }
