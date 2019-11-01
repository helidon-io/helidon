package io.helidon.microprofile.messaging;

import io.helidon.microprofile.messaging.beans.InternalChannelsBean;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessagingCdiExtensionTest extends AbstractCDITest {

    @Override
    void cdiBeanClasses(Set<Class<?>> classes) {
        classes.add(InternalChannelsBean.class);
    }

    @Test
    void internalChannelsInSameBeanTest() throws InterruptedException {
        // Wait till all messages are delivered
        assertTrue(InternalChannelsBean.selfCallLatch.await(10, TimeUnit.SECONDS)
                , "All messages not delivered in time, number of unreceived messages: "
                        + InternalChannelsBean.selfCallLatch.getCount());
    }
}
