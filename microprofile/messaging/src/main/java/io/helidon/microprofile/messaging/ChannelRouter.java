package io.helidon.microprofile.messaging;

import io.helidon.config.Config;
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelRouter {
    private List<AbstractConnectableChannelMethod> connectableBeanMethods = new ArrayList<>();
    private Map<String, List<IncomingSubscriber>> incomingSubscriberMap = new HashMap<>();

    private Map<String, Bean<?>> incomingConnectorFactoryMap = new HashMap<>();
    private Map<String, Bean<?>> outgoingConnectorFactoryMap = new HashMap<>();

    public void registerBeanReference(Bean<?> bean) {
        connectableBeanMethods.stream()
                .filter(m -> m.getDeclaringType() == bean.getBeanClass())
                .forEach(m -> m.setDeclaringBean(bean));
    }

    public void connect(BeanManager beanManager) {
        Config config = ((MpConfig) ConfigProvider.getConfig()).helidonConfig();
        connectableBeanMethods.forEach(m -> m.connect(beanManager, config));
    }

    void addIncomingMethod(AnnotatedMethod m) {
        IncomingSubscriber incomingSubscriber = new IncomingSubscriber(m, this);
        String channelName = incomingSubscriber.getChannelName();
        List<IncomingSubscriber> namedIncomings = incomingSubscriberMap.getOrDefault(channelName, new ArrayList<>());
        namedIncomings.add(incomingSubscriber);
        incomingSubscriberMap.put(channelName, namedIncomings);
        connectableBeanMethods.add(incomingSubscriber);
    }

    void addOutgoingMethod(AnnotatedMethod m) {
        OutgoingPublisher outgoingPublisher = new OutgoingPublisher(m, this);
        connectableBeanMethods.add(outgoingPublisher);
    }

    void addConnectorFactory(Bean<?> bean) {
        Class<?> beanType = bean.getBeanClass();
        Connector annotation = beanType.getAnnotation(Connector.class);
        if (IncomingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            incomingConnectorFactoryMap.put(annotation.value(), bean);
        } else if (OutgoingConnectorFactory.class.isAssignableFrom(beanType) && null != annotation) {
            outgoingConnectorFactoryMap.put(annotation.value(), bean);
        }
    }

    public List<IncomingSubscriber> getIncomingSubscribers(String channelName) {
        return incomingSubscriberMap.get(channelName);
    }

    public Bean<?> getIncomingConnectorFactory(String connectorName) {
        return incomingConnectorFactoryMap.get(connectorName);
    }

    public Bean<?> getOutgoingConnectorFactory(String connectorName) {
        return outgoingConnectorFactoryMap.get(connectorName);
    }

}
