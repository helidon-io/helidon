package io.helidon.integrations.micronaut.cdi;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;

@MicronautIntercepted
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class MicronautInterceptor {
    private static final Logger LOGGER = Logger.getLogger(MicronautInterceptor.class.getName());

    private final ApplicationContext context;
    private final MicronautCdiExtension extension;

    @Inject
    MicronautInterceptor(ApplicationContext context, MicronautCdiExtension extension) {
        this.context = context;
        this.extension = extension;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @AroundInvoke
    public Object invoke(InvocationContext cdiCtx) {

        Method javaMethod = cdiCtx.getMethod();
        MethodInterceptorMetadata meta = extension.getInterceptionMetadata(javaMethod);

        Set<Class<? extends MethodInterceptor<?, ?>>> interceptorClasses = meta.interceptors();
        Set<MethodInterceptor<?, ?>> interceptors = new TreeSet<>(Comparator.comparingInt(MethodInterceptor::getOrder));

        for (Class<? extends MethodInterceptor> aClass : interceptorClasses) {
            interceptors.add(context.findBean(aClass)
                                     .orElseThrow(() -> new MicronautCdiException("Cannot create bean class for interceptor " + aClass
                                             .getName())));
        }

        ExecutableMethod<?, ?> executableMethod = meta.executableMethod();
        Iterator<MethodInterceptor<?, ?>> remaining = interceptors.iterator();
        io.micronaut.aop.MethodInvocationContext context = MicronautMethodInvocationContext
                .create(cdiCtx, executableMethod, interceptors, remaining);

        MethodInterceptor<?, ?> next = remaining.next();
        LOGGER.info("Micronaut interceptor: " + next.getClass().getName());
        return next.intercept(context);
    }
}
