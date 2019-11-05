package io.helidon.microprofile.messaging;

import io.helidon.config.Config;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;

import java.lang.reflect.Method;

public abstract class AbstractConnectableChannelMethod {

    protected final String channelName;

    protected Bean<?> bean;
    private ChannelRouter router;
    protected Method method;
    protected Object beanInstance;
    protected BeanManager beanManager;
    protected Config config;

    public AbstractConnectableChannelMethod(String channelName, Method method, ChannelRouter router) {
        this.router = router;
        this.method = method;
        this.channelName = channelName;
        validate();
    }

    private void validate() {
        if (channelName == null || channelName.trim().isEmpty()) {
            throw new DeploymentException("Missing channel name in annotation @Incoming/@Outgoing on method "
                    + method.toString());
        }
    }

    protected abstract void connect();

    public void connect(BeanManager beanManager, Config config) {
        this.beanInstance = getBeanInstance(bean, beanManager);
        this.beanManager = beanManager;
        this.config = config;
        connect();
    }

    public void setDeclaringBean(Bean bean) {
        this.bean = bean;
    }

    public Class<?> getDeclaringType() {
        return method.getDeclaringClass();
    }

    public String getChannelName() {
        return channelName;
    }

    protected Object getBeanInstance(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext creationalContext = beanManager.createCreationalContext(bean);
            return beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        return instance;
    }

    public ChannelRouter getRouter() {
        return router;
    }
}
