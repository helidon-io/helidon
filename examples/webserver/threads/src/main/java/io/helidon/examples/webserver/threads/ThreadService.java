package io.helidon.examples.webserver.threads;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.lang.System.Logger.Level;
import java.util.concurrent.RejectedExecutionException;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class ThreadService implements HttpService {

    private static final System.Logger LOGGER = System.getLogger(ThreadService.class.getName());
    private static final Random rand = new Random(System.currentTimeMillis());

    // ThreadPool of platform threads.
    private static ExecutorService platformExecutorService;
    // Executor of virtual threads.
    private static ExecutorService virtualExecutorService;

    WebClient client = WebClient.builder()
            .baseUri("http://localhost:8080/thread")
            .build();

    /**
     * The config value for the key {@code greeting}.
     */

    ThreadService() {
        this(Config.global().get("app"));
    }

    ThreadService(Config appConfig) {
        /*
         * We create two executor services. One is a thread pool of platform threads.
         * The second is a virtual thread executor service.
         * See `application.yaml` for configuration of each of these.
         */
        ThreadPoolSupplier platformThreadPoolSupplier = ThreadPoolSupplier.builder()
                .config(appConfig.get("application-platform-executor"))
                .build();
        platformExecutorService = platformThreadPoolSupplier.get();

        ThreadPoolSupplier virtualThreadPoolSupplier = ThreadPoolSupplier.builder()
                .config(appConfig.get("application-virtual-executor"))
                .build();
        virtualExecutorService = virtualThreadPoolSupplier.get();
    }

    /**
     * A service registers itself by updating the routing rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public void routing(HttpRules rules) {
        rules
                .get("/compute", this::computeHandler)
                .get("/compute/{iterations}", this::computeHandler)
                .get("/fanout", this::fanOutHandler)
                .get("/fanout/{count}", this::fanOutHandler)
                .get("/sleep", this::sleepHandler)
                .get("/sleep/{seconds}", this::sleepHandler);
    }

    /**
     * Perform a CPU intensive operation.
     * The optional path parameter controls the number of iterations of the computation. The more
     * iterations the longer it will take.
     *
     * @param request server request
     * @param response server response
     */
    private void computeHandler(ServerRequest request, ServerResponse response) {
        String iterations = request.path().pathParameters().first("iterations").orElse("1");
        try {
            // We execute the computation on a platform thread. This prevents pining of the virtual
            // thread, plus provides us the ability to limit the number of concurrent computation requests
            // we handle by limiting the thread pool work queue length (as defined in application.yaml)
            Future<Double> future = platformExecutorService.submit(() -> compute(Integer.parseInt(iterations)));
            response.send(future.get().toString());
        } catch (RejectedExecutionException e) {
            // Work queue is full! We reject the request
            LOGGER.log(Level.WARNING, e);
            response.status(Status.SERVICE_UNAVAILABLE_503).send("Server busy");
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.log(Level.ERROR, e);
            response.status(Status.INTERNAL_SERVER_ERROR_500).send();
        }
    }

    /**
     * Sleep for a specified number of secons.
     * The optional path parameter controls the number of seconds to sleep. Defaults to 1
     *
     * @param request server request
     * @param response server response
     */
    private void sleepHandler(ServerRequest request, ServerResponse response) {
        String seconds = request.path().pathParameters().first("seconds").orElse("1");
        response.send(Integer.toString(sleep(Integer.parseInt(seconds))));
    }

    /**
     * Fan out a number of remote requests in parallel.
     * The optional path parameter controls the number of parallel requests to make.
     *
     * @param request server request
     * @param response server response
     */
    private void fanOutHandler(ServerRequest request, ServerResponse response) {
        int count = Integer.parseInt(request.path().pathParameters().first("count").orElse("1"));
        LOGGER.log(Level.INFO, "Fanning out " + count + " parallel requests");
        // We simulate multiple client requests running in parallel by calling our sleep endpoint.
        try {
            // For this we use our virtual thread based executor. We submit the work and save the Futures
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < count; i++) {
                futures.add(virtualExecutorService.submit(() -> callRemote(rand.nextInt(5))));
            }

            // After work has been submitted we loop through the future and block getting the results.
            // We aggregate the results in a list of Strings
            var responses = new ArrayList<String>();
            for (var future : futures) {
                try {
                    responses.add(future.get());
                } catch (InterruptedException e) {
                    responses.add(e.getMessage());
                }
            }

            // All parallel calls are complete!
            response.send(String.join(":", responses));
        } catch (ExecutionException e) {
            LOGGER.log(Level.ERROR, e);
            response.status(Status.INTERNAL_SERVER_ERROR_500).send();
        }
    }

    /**
     * Simulate a remote client call be calling the sleep endpoint on ourself.
     *
     * @param seconds number of seconds the endpoint should sleep.
     * @return
     */
    private String callRemote(int seconds) {
        LOGGER.log(Level.INFO, Thread.currentThread() + ": Calling remote sleep for " + seconds + "s");
        try (HttpClientResponse response = client.get("/sleep/" + seconds).request()) {
            if (response.status().equals(Status.OK_200)) {
                return response.as(String.class);
            } else {
                return (response.status().toString());
            }
        }
    }

    /**
     * Sleep current thread
     * @param seconds number of seconds to sleep
     * @return
     */
    private int sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1_000L);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, e);
        }
        return seconds;
    }

    /**
     * Perform a CPU intensive computation
     * @param iterations: number of times to perform computation
     */
    private double compute(int iterations) {
        LOGGER.log(Level.INFO, Thread.currentThread() + ": Computing with " + iterations + " iterations");
        double d = 123456789.123456789 * rand.nextInt(100);
        for (int i=0; i < iterations; i++) {
            for (int n=0; n < 1_000_000; n++) {
                for (int j = 0; j < 5; j++) {
                    d = Math.tan(d);
                    d = Math.atan(d);
                }
            }
        }
        return d;
    }
}
