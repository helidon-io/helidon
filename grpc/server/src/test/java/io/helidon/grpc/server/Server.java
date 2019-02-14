package io.helidon.grpc.server;


import io.helidon.config.Config;

import java.util.logging.LogManager;


/**
 * @author Aleksandar Seovic  2019.02.06
 */
public class Server
    {
    public static void main(String[] args) throws Exception
        {

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Server.class.getResourceAsStream("/logging.properties"));

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfig serverConfig =
                GrpcServerConfig.create(config.get("grpc"));

        GrpcServer server = GrpcServer.create(serverConfig, createRouting(config));

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        server.start()
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
        }

    private static GrpcRouting createRouting(Config config)
        {
        GreetService greetService = new GreetService(config);
        return GrpcRouting.builder()
                .register(greetService)
                .build();
        }
    }
