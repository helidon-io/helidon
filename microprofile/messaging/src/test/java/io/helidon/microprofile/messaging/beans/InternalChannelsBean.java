package io.helidon.microprofile.messaging.beans;

import io.helidon.common.reactive.Multi;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ApplicationScoped
public class InternalChannelsBean {

    private static Set<String> TEST_DATA = new HashSet<>(Arrays.asList("test1", "test2"));
    public static CountDownLatch selfCallLatch = new CountDownLatch(TEST_DATA.size());

    @Outgoing("self-call-channel")
    public Publisher<String> produceMessage() {
//        return Flowable.fromIterable(TEST_DATA);
        //Nobody needs javarx
        return Multi.<String>justMP(TEST_DATA.toArray(new String[0]));
    }

    @Incoming("self-call-channel")
    public void receiveFromSelfMethod(String msg) {
        assertTrue(TEST_DATA.contains(msg), "Unexpected message received");
        selfCallLatch.countDown();
    }
}
