package io.helidon.grpc.server;


import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import java.util.logging.LogManager;


/**
 * @author Aleksandar Seovic  2019.02.06
 */
public class Server
    {
    public static void main(String[] args) throws Exception
        {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Server.class.getResourceAsStream("/logging.properties"));

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfig serverConfig =
                GrpcServerConfig.create(config.get("grpc"));

        GrpcServer grpcServer = GrpcServer.create(serverConfig, createRouting(config));

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        grpcServer.start()
                .thenAccept(s ->
                        {
                        System.out.println("gRPC server is UP! http://localhost:" + s.port());
                        s.whenShutdown().thenRun(() -> System.out.println("gRPC server is DOWN. Good bye!"));
                        })
                .exceptionally(t ->
                        {
                        System.err.println("Startup failed: " + t.getMessage());
                        t.printStackTrace(System.err);
                        return null;
                        });

        // add support for standard and gRPC health checks
        HealthSupport health = HealthSupport.builder()
                .add(HealthChecks.healthChecks())
                .add(grpcServer.healthChecks())
                .build();

        // start web server with metrics and health endpoints
        Routing routing = Routing.builder()
                .register(health)
                .register(MetricsSupport.create())
                .build();

        ServerConfiguration webServerConfig = ServerConfiguration.create(config.get("webserver"));

        WebServer.create(webServerConfig, routing)
                .start()
                .thenAccept(s ->
                    {
                    System.out.println("HTTP server is UP! http://localhost:" + s.port());
                    s.whenShutdown().thenRun(() -> System.out.println("HTTP server is DOWN. Good bye!"));
                    })
                .exceptionally(t ->
                    {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                    });
        }

    private static GrpcRouting createRouting(Config config)
        {
        GreetService     greetService     = new GreetService(config);
        GreetServiceJava greetServiceJava = new GreetServiceJava(config);

        return GrpcRouting.builder()
                .intercept(GrpcMetrics.create())
                .register(greetService, cfg -> cfg.intercept(LoggingServerInterceptor.create("foo")))
                .register(greetServiceJava)
                .register(new StringService())
                .build();
        }
    }
