package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LifecycleMethodTest {
    private static final TestFeature FEATURE = new TestFeature();

    static void setUpRoute(HttpRouting.Builder http) {
        http.addFeature(FEATURE);
    }

    @BeforeEach
    void resetCounter() {
        FEATURE.reset();
    }

    @RepeatedTest(10)
    void testLifecycleMethodsCalled() {
        WebServer server = WebServer.builder()
                .routing(LifecycleMethodTest::setUpRoute)
                .build()
                .start();
        server.start();
        assertThat("Before start should have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should not have been called on server startup", FEATURE.afterStop.get(), is(0));

        server.stop();
        assertThat("Before start should only have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should have been called on server stop", FEATURE.afterStop.get(), is(1));
    }

    private static final class TestFeature implements HttpFeature {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.get("/", (req, res) -> res.send("works"));
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }
}
