package io.helidon.security.integration.grpc;


import io.helidon.config.Config;
import io.helidon.grpc.server.GrpcMetrics;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import io.helidon.security.Security;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import java.util.logging.LogManager;


/**
 * @author Aleksandar Seovic  2019.02.06
 */
public class SecureGrpcServer
    {
    public static void main(String[] args) throws Exception
        {
        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                SecureGrpcServer.class.getResourceAsStream("/logging.properties"));

        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();

        Routing routing = Routing.builder()
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

        // Get gRPC server config from the "grpc" section of application.yaml
        GrpcServerConfiguration serverConfig =
                GrpcServerConfiguration.create(config.get("grpc"));

        GrpcServer grpcServer = GrpcServer.create(serverConfig, createRouting(security, config));

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
        }

    private static GrpcRouting createRouting(Security security, Config config)
        {
        GreetService     greetService     = new GreetService(config);
        GreetServiceJava greetServiceJava = new GreetServiceJava(config);

        return GrpcRouting.builder()
                .intercept(GrpcMetrics.create())
                .intercept(GrpcSecurity.create(security).securityDefaults(GrpcSecurity.authenticate()))
                .register(greetService, GrpcSecurity.rolesAllowed("user"))
                .register(greetServiceJava)
                .register(new StringService())
                .build();
        }
    }
