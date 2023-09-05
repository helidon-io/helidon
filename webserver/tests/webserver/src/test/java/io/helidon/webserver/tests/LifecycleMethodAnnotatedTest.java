package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class LifecycleMethodAnnotatedTest {
    private static final TestFeature FEATURE = new TestFeature();

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder http) {
        http.addFeature(FEATURE);
    }

    @AfterAll
    static void afterAll() {
        assertThat("Before start should only have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should have been called on server stop", FEATURE.afterStop.get(), is(1));
    }

    @Test
    void testLifecycleMethodsCalled(WebClient webClient) {
        ClientResponseTyped<String> request = webClient.get()
                .request(String.class);

        assertThat(request.entity(), is("works"));

        assertThat("Before start should have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should not have been called on server startup", FEATURE.afterStop.get(), is(0));
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
