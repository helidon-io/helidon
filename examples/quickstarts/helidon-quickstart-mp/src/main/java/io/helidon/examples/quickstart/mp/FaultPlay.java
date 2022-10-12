package io.helidon.examples.quickstart.mp;

import io.helidon.microprofile.faulttolerance.retrypolicy.RetryExponentialBackoff;
import io.helidon.microprofile.faulttolerance.retrypolicy.RetryFibonacciBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@Path("/fault")
public class FaultPlay {

    private AtomicInteger counter = new AtomicInteger(0);

    @Inject
    @RestClient
    Client client;

    @Retry(maxRetries = 5)
    private String getResult(){
        String it = client.getIt();
        return it;
    }

    private void retryer(){
        System.out.println("Retry on >>> "+counter.incrementAndGet());
        throw new RuntimeException();
    }

    @GET
    @Retry(maxDuration = 200, maxRetries = 200)
    @RetryFibonacciBackoff
    public String getSomething(){
        retryer();
        return null;
    }

}
