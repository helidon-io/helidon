package io.helidon.integrations.micronaut.cdi;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentValue;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MicronautMethodInvocationContext implements MethodInvocationContext {
    private static final Logger LOGGER = Logger.getLogger(MicronautMethodInvocationContext.class.getName());

    private final InvocationContext cdiContext;
    private final ExecutableMethod executableMethod;
    private final Set<MethodInterceptor<?, ?>> allInterceptors;
    private final MutableConvertibleValues attributes;

    private Iterator<MethodInterceptor<?, ?>> remaining;
    private Map<String, MutableArgumentValue<?>> mutableArguments;

    private MicronautMethodInvocationContext(InvocationContext cdiContext,
                                             ExecutableMethod executableMethod,
                                             Set<MethodInterceptor<?, ?>> allInterceptors,
                                             Iterator<MethodInterceptor<?, ?>> remaining) {
        this.cdiContext = cdiContext;
        this.executableMethod = executableMethod;
        this.allInterceptors = allInterceptors;
        this.remaining = remaining;

        this.attributes = new MutableConvertibleMultiValuesMap();
        this.mutableArguments = new LinkedHashMap<>();
        Object[] parameters = cdiContext.getParameters();
        Argument[] argumentNames = executableMethod.getArguments();

        for (int i = 0; i < argumentNames.length; i++) {
            Argument argument = argumentNames[i];
            Object parameterValue = parameters[i];
            mutableArguments.put(argument.getName(),
                                 new MutableArgument<Object>(argument, parameterValue));
        }
    }

    public static MethodInvocationContext create(InvocationContext cdiCtx,
                                                 ExecutableMethod<?, ?> executableMethod,
                                                 Set<MethodInterceptor<?, ?>> allInterceptors,
                                                 Iterator<MethodInterceptor<?, ?>> remaining) {
        return new MicronautMethodInvocationContext(cdiCtx,
                                                    executableMethod,
                                                    allInterceptors,
                                                    remaining);
    }

    @Override
    public ExecutableMethod getExecutableMethod() {
        return executableMethod;
    }

    @Override
    public Map<String, MutableArgumentValue<?>> getParameters() {
        return mutableArguments;
    }

    @Override
    public Object getTarget() {
        return cdiContext.getTarget();
    }

    @Override
    public Object proceed() throws RuntimeException {
        if (remaining.hasNext()) {
            MethodInterceptor<?, ?> next = remaining.next();
            LOGGER.info("Micronaut interceptor: " + next.getClass().getName());
            return next.intercept(this);
        }
        try {
            Object[] arguments = mutableArguments.values()
                    .stream()
                    .map(ArgumentValue::getValue)
                    .toArray(Object[]::new);
            cdiContext.setParameters(arguments);

            LOGGER.info("Proceeding with CDI interceptors");
            return cdiContext.proceed();
        } catch (Exception e) {
            throw new MicronautCdiException("Failed to invoke intercepted method", e);
        }
    }

    @Override
    public Object proceed(Interceptor from) throws RuntimeException {
        this.remaining = allInterceptors.iterator();
        while (remaining.hasNext()) {
            MethodInterceptor<?, ?> next = remaining.next();
            if (next.equals(from)) {
                return next.intercept(this);
            }
        }
        return proceed();
    }

    @Override
    public Method getTargetMethod() {
        return cdiContext.getMethod();
    }

    @Override
    public ReturnType getReturnType() {
        return executableMethod.getReturnType();
    }

    @Override
    public Class getDeclaringType() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public String getMethodName() {
        return executableMethod.getName();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Argument[] getArguments() {
        return executableMethod.getArguments();
    }

    @Override
    public Object invoke(Object instance, Object... arguments) {
        try {
            return getTargetMethod().invoke(instance, arguments);
        } catch (Exception e) {
            throw new MicronautCdiException(e);
        }
    }

    private static class MutableArgument<T> implements MutableArgumentValue<T> {
        private final Argument<T> argument;
        private T value;

        private MutableArgument(Argument<T> argument, T value) {
            this.argument = argument;
            this.value = value;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public void setValue(T value) {
            if (getType().isInstance(value)) {
                this.value = ConversionService.SHARED.convert(value, getType())
                        .orElseThrow(() ->  new IllegalArgumentException("Invalid value [" + value + "] for argument: " + this)
                );
            } else {
                this.value = value;
            }
        }

        @Override
        public String getName() {
            return argument.getName();
        }

        @Override
        public Class<T> getType() {
            return argument.getType();
        }

        @Override
        public boolean equalsType(Argument<?> other) {
            return argument.equalsType(other);
        }

        @Override
        public int typeHashCode() {
            return argument.getType().hashCode();
        }
    }
}
