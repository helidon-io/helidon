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

    static { //todo move to AQ client as it's specific to that
        System.setProperty("oracle.jdbc.fanEnabled", "false"); //silence benign message re ONS
    }

    /**
     * Init the hooks/callbacks pre and post processing of methods annotated with @LRA and messaging annotation
     * All three callbacks (beforeMethodInvocation, afterMethodInvocation, and onMethodInvocationFailure)
     * apply to all three messaging combinations (@Incoming, @Outgoing, or both/processors)
     * Priority is PLATFORM_AFTER + 102 right afer PLATFORM_AFTER + 101 which is used for messaging system.
     *
     * @param event       unused
     * @param beanManager used to register callbacks
     */
    private void init(@Observes @Priority(PLATFORM_AFTER + 100) @Initialized(ApplicationScoped.class) Object event,
                      BeanManager beanManager) {
        messagingCdiExtension = beanManager.getExtension(MessagingCdiExtension.class);
        messagingCdiExtension.beforeMethodInvocation(this::beforeMethodInvocation);
        messagingCdiExtension.afterMethodInvocation(this::afterMethodInvocation);
        messagingCdiExtension.onMethodInvocationFailure(this::onMethodInvocationFailure);
        Config config = ConfigProvider.getConfig();
    }


    /**
     * Pre-processing that essentially delegates to ServerLRAMessagingFilter
     *
     * @param method  the key used to later retrieve the ServerLRAMessagingFilter in afterMethodInvocation
     *                Although there is only one MessagingMethod per application method, this is a safe key as
     *                the MP messaging spec states taht messaging methods must be executed serially.
     * @param message incoming message
     * @return long unused but -1 indicating failure, 0 indicating success
     */
    long beforeMethodInvocation(MessagingMethod method, Object message) {
        boolean isAnnotatedWithLRA = method.getMethod().getDeclaringClass().getDeclaredAnnotation(LRA.class) == null;
        LOGGER.info("beforeMethodInvocation method:" + method.getMethod().getName() +
                " message:" + message +  "isAnnotatedWithLRA:" + isAnnotatedWithLRA);
        if (!isAnnotatedWithLRA) return 0;
        if (!(message instanceof Message)) {
            LOGGER.warning("LRACdiExtension.beforeMethodInvocation !(message instanceof Message) returning");
            return -1;
        }
        ServerLRAMessagingFilter serverLRAMessagingFilter = new ServerLRAMessagingFilter(method.getMethod());
        serverToLRAMessageFilterMap.put(method, serverLRAMessagingFilter);
        serverLRAMessagingFilter.beforeMethodInvocation((Message) message);
//        showMessageProperties("beforeMethodInvocation", method, message);
        return 0;
    }

    /**
     * Post-processing that essentially delegates to ServerLRAMessagingFilter
     *
     * @param method  the key to retrieve the ServerLRAMessagingFilter that was added in beforeMethodInvocation
     *                and so must be removed to avoid leak.
     * @param message outgoing message that appropriate headers/properties will be added to depending on
     *                whether it is LRA method and if so if it is reply, calling another microservice, or
     *                replying from complete, compensate, etc.
     * @return long unused but -1 indicating failure, 0 indicating success
     */
    long afterMethodInvocation(MessagingMethod method, Object message) {
//        showAllMessageProperties(message);
        try {
            LOGGER.info("afterMethodInvocation method:" + method.getMethod().getName() + " message:" + message);
            ServerLRAMessagingFilter serverLRAMessagingFilter = serverToLRAMessageFilterMap.get(method);
            if (serverLRAMessagingFilter == null) return 0;
            serverLRAMessagingFilter.afterMethodInvocation(method, message);
//            showMessageProperties("afterMethodInvocation", method, message);
            return 0;
        } finally {
            serverToLRAMessageFilterMap.remove(method);
        }
    }

    /**
     * Failure case. Should result in LRA cancel if appropriate
     *
     * @param method    the key to retrieve the ServerLRAMessagingFilter that was added in beforeMethodInvocation
     *                  and so must be removed to avoid leak.
     * @param message   outoing message that will not be sent due to failure
     * @param throwable the exception that was thrown
     * @return long unused but -1 indicating failure, 0 indicating success
     */
    long onMethodInvocationFailure(MessagingMethod method, Message<?> message, Throwable throwable) {
        try {
            serverToLRAMessageFilterMap.get(method).setCancel();
            LOGGER.fine("LRACdiExtension.onMethodInvocationFailure " +
                    "method = " + method + ", message = " + message + ", t = " + throwable);
            showMessageProperties("onMethodInvocationFailure" , method, message);
            return 0;
        } finally {
            serverToLRAMessageFilterMap.remove(method);
        }
    }


    /**
     * extend these and add @Provider annotation
     * ClientLRARequestFilter...
     * public void filter(ClientRequestContext context) {
     * if (Current.peek() != null) {
     * context.setProperty(LRA_HTTP_CONTEXT_HEADER, Current.peek());
     * }
     * Current.updateLRAContext(context);
     * }
     * <p>
     * ClientLRAResponseFilter ...
     * public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
     * Object callingContext = requestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);
     * <p>
     * if (callingContext != null) {
     * Current.push((URI) callingContext);
     * }
     * }
     * }
     */
    private void showMessageProperties(String hookname, MessagingMethod method, Object o) {
        try {
            if (o instanceof AqMessage) {
                LOGGER.info("LRACdiExtension." + hookname + " method:" + method.getMethod().getName() + " HELIDONLRAOPERATION:" +
                        "" + ((AqMessage) o).getJmsMessage().getStringProperty("HELIDONLRAOPERATION"));
                LOGGER.info("LRACdiExtension." + hookname + " method:" + method.getMethod().getName() + " LRA_HTTP_CONTEXT_HEADER:" +
                        ((AqMessage) o).getJmsMessage().getStringProperty("LRA_HTTP_CONTEXT_HEADER"));
            } else if (o instanceof KafkaMessage) {
                if (((KafkaMessage) o).getHeaders() != null) {
                    LOGGER.info("LRACdiExtension." + hookname + " method:" + method.getMethod().getName() + " HELIDONLRAOPERATION:" +
                            "" + ((KafkaMessage) o).getHeaders().headers("HELIDONLRAOPERATION"));
                    LOGGER.info("LRACdiExtension." + hookname + " method:" + method.getMethod().getName() + " LRA_HTTP_CONTEXT_HEADER:" +
                            ((KafkaMessage) o).getHeaders().headers("LRA_HTTP_CONTEXT_HEADER"));
                }
            } else {
                LOGGER.info("LRACdiExtension." + hookname + " method:" + method.getMethod() + " o is not AqMessage nor KafkaMessage o:" + o);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private void showAllMessageProperties(Object o) {
        if (o instanceof AqMessage) {
            try {
                Iterator iterator = ((AqMessage)o).getJmsMessage().getPropertyNames().asIterator();
                while(iterator.hasNext()) {
                    LOGGER.fine("LRACdiExtension.AqMessage property:" + iterator.next());
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        } else if (o instanceof io.helidon.messaging.connectors.kafka.KafkaMessage) {
            try {
                Iterator iterator = ((AqMessage)o).getJmsMessage().getPropertyNames().asIterator();
                while(iterator.hasNext()) {
                    LOGGER.fine("LRACdiExtension.KafkaMessage property:" + iterator.next());
                }
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }
}
