package io.helidon.lra.messaging;


import io.helidon.messaging.connectors.aq.AqMessage;
import io.helidon.messaging.connectors.kafka.KafkaMessage;
import io.helidon.microprofile.messaging.MessagingCdiExtension;
import io.helidon.microprofile.messaging.MessagingMethod;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.config.Config;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.jms.JMSException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * This extension is the equivalent in functionality of JAX-RS listening registerer
 */
public class LRACdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(LRACdiExtension.class.getName());
    MessagingCdiExtension messagingCdiExtension;
    Map<Long, Message<?>> txMap = new ConcurrentHashMap<>();
    Map<MessagingMethod, ServerLRAMessagingFilter> serverToLRAMessageFilterMap = new ConcurrentHashMap<>();

    /**
     * Init the hooks/callbacks pre and post processing of methods annotated with @LRA and messaging annotation
     *  All three callbacks (beforeMethodInvocation, afterMethodInvocation, and onMethodInvocationFailure)
     *  apply to all three messaging combinations (@Incoming, @Outgoing, or both/processors)
     *  Priority is PLATFORM_AFTER + 102 right afer PLATFORM_AFTER + 101 which is used for messaging system.
     * @param event unused
     * @param beanManager used to register callbacks
     */
    private void init(@Observes @Priority(PLATFORM_AFTER + 102) @Initialized(ApplicationScoped.class)  Object event,
                                 BeanManager beanManager) {
        messagingCdiExtension = beanManager.getExtension(MessagingCdiExtension.class);
        messagingCdiExtension.beforeMethodInvocation(this::beforeMethodInvocation);
        messagingCdiExtension.afterMethodInvocation(this::afterMethodInvocation);
        messagingCdiExtension.onMethodInvocationFailure(this::onMethodInvocationFailure);
        Config config = ConfigProvider.getConfig();
    }


    /**
     * Pre-processing that essentially delegates to ServerLRAMessagingFilter
     * @param method the key used to later retrieve the ServerLRAMessagingFilter in afterMethodInvocation
     *               Although there is only one MessagingMethod per application method, this is a safe key as
     *               the MP messaging spec states taht messaging methods must be executed serially.
     * @param message
     * @return long unused but -1 indicating failure, 0 indicating success
     */
    long beforeMethodInvocation(MessagingMethod method, Object message) {
        boolean isAnnotatedWithLRA = method.getMethod().getDeclaringClass().getDeclaredAnnotation(LRA.class) == null;
        LOGGER.info("LRACdiExtension.beforeMethodInvocation isAnnotatedWithLRA:" + isAnnotatedWithLRA + " method:" + method + "object:" + message);
        if(!isAnnotatedWithLRA) return 0;
        if (!(message instanceof Message)) return -1;
        ServerLRAMessagingFilter serverLRAMessagingFilter = new ServerLRAMessagingFilter(method.getMethod());
        serverToLRAMessageFilterMap.put(method, serverLRAMessagingFilter);
        Config config = method.getIncomingChannelConfig(); //todo this is only the config for the method annotated with incoming - if it was the config of all
        serverLRAMessagingFilter.beforeMethodInvocation((Message)message);
        showMessageProperties(message);
        return 0;
    }

    long afterMethodInvocation(MessagingMethod method, Object message) {
        try {
        LOGGER.info("LRACdiExtension.afterMethodInvocation method = " + method + ", o = " + message);
        ServerLRAMessagingFilter serverLRAMessagingFilter = serverToLRAMessageFilterMap.get(method);
        if (serverLRAMessagingFilter == null) return 0 ;
        serverLRAMessagingFilter.afterMethodInvocation(method, message);
        showMessageProperties(message);
        return 0;
        } finally {
            serverToLRAMessageFilterMap.remove(method);
        }
    }

    /**
     * extend these and add @Provider annotation
     ClientLRARequestFilter...
     public void filter(ClientRequestContext context) {
     if (Current.peek() != null) {
     context.setProperty(LRA_HTTP_CONTEXT_HEADER, Current.peek());
     }
     Current.updateLRAContext(context);
     }

     ClientLRAResponseFilter ...
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
    Object callingContext = requestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);

    if (callingContext != null) {
    Current.push((URI) callingContext);
    }
    }
    }
     */

    long onMethodInvocationFailure(MessagingMethod method, Message<?> message, Throwable t) {
        try {
            serverToLRAMessageFilterMap.get(method).setCancel();
            LOGGER.fine("LRACdiExtension.onMethodInvocationFailure " +
                    "method = " + method + ", message = " + message + ", t = " + t);
            showMessageProperties(message);
            return 0;
        } finally {
            serverToLRAMessageFilterMap.remove(method);
        }
    }

    private void showMessageProperties(Object o) {
        if (true) return; //todo remove
        try {
            if (o instanceof AqMessage) {
                LOGGER.fine("LRACdiExtension.showMessageProperties HelidonLRAOperation:" +
                        "" + ((AqMessage) o).getJmsMessage().getStringProperty("HelidonLRAOperation"));
                LOGGER.fine("LRACdiExtension.showMessageProperties org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER:" +
                        ((AqMessage) o).getJmsMessage().getStringProperty("org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER"));
            } else if (o instanceof KafkaMessage) {
                if (((KafkaMessage) o).getHeaders() != null) {
                    LOGGER.fine("LRACdiExtension.showMessageProperties HelidonLRAOperation:" +
                            "" + ((KafkaMessage) o).getHeaders().headers("HelidonLRAOperation"));
                    LOGGER.fine("LRACdiExtension.showMessageProperties org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER:" +
                            ((KafkaMessage) o).getHeaders().headers("org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER"));
                }
            } else {
                LOGGER.fine("LRACdiExtension.showMessageProperties o is not AqMessage nor KafkaMessage o:" + o);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

}
