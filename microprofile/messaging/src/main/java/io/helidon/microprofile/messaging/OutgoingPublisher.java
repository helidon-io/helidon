package io.helidon.microprofile.messaging;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.microprofile.config.MpConfig;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.enterprise.inject.spi.AnnotatedMethod;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

//TODO: remove publisher implementation, it doesnt make sense here(rename class too)
public class OutgoingPublisher extends AbstractConnectableChannelMethod implements Publisher<Message<?>> {

    private static final Logger LOGGER = Logger.getLogger(OutgoingPublisher.class.getName());

    private List<Subscriber<? super Message<?>>> subscriberList = new ArrayList<>();
    private SubscriberBuilder<? extends Message<?>, Void> subscriberBuilder;

    public OutgoingPublisher(AnnotatedMethod method, ChannelRouter router) {
        super(method.getAnnotation(Outgoing.class).value(), method.getJavaMember(), router);
    }

    public void connect() {

        try {
            //TODO: Types?
            Publisher result = (Publisher) method.invoke(beanInstance);

            Config channelConfig = config.get("mp.messaging.outgoing").get(channelName);
            ConfigValue<String> connectorName = channelConfig.get("connector").asString();
            if (connectorName.isPresent()) {
                subscriberBuilder = ((OutgoingConnectorFactory) getBeanInstance(getRouter()
                        .getOutgoingConnectorFactory(connectorName.get()), beanManager))
                        .getSubscriberBuilder(MpConfig.builder().config(channelConfig).build());
                result.subscribe(subscriberBuilder.build());
            } else {
                // Connect to Incoming methods
                List<IncomingSubscriber> incomingSubscribers = getRouter().getIncomingSubscribers(getChannelName());
                if (incomingSubscribers != null) {
                    for (IncomingSubscriber s : getRouter().getIncomingSubscribers(getChannelName())) {
                        //TODO: get rid of reactivex
                        //((Flowable)result).observeOn(Schedulers.computation()).subscribe(o -> s.onNext(Message.of(o)));
                        //result.subscribe(new ConsumableSubscriber(m -> s.onNext(Message.of(m))));
                        ReactiveStreams.fromPublisher(result).to(s).run();
//                        publisherBuilder.buildRs().subscribe(new ConsumableSubscriber(m -> s.onNext(Message.of(m))));
//                        publisherBuilder.buildRs().
                    }
                }

            }


        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }


    }

    @Override
    public void subscribe(Subscriber<? super Message<?>> subscriber) {

    }
}
