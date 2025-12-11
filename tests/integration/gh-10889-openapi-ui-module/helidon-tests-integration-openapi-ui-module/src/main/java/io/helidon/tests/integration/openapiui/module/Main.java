
package io.helidon.tests.integration.openapiui.module;


import io.helidon.integrations.openapi.ui.OpenApiUi;
import io.helidon.logging.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.openapi.OpenApiFeature;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;




/**
 * The application main class.
 */
public class Main {


    /**
     * Cannot be instantiated.
     */
    private Main() {
    }


    /**
     * Application main entry point.
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        
        // load logging configuration
        LogConfig.configureRuntime();

        // initialize global config from default configuration
        Config config = Config.create();
        Config.global(config);

        var openApiConfig = config.get("openapi");
        var openApiUiConfig = openApiConfig.get("ui");
        var ui = OpenApiUi.builder()
                .config(openApiUiConfig)
                .webContext("/my-ui")
                .build();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .addFeature(OpenApiFeature.builder()
                                    .addService(ui)
                                    .build())
                .config(config.get("openapi"))
                .routing(Main::routing)
                .build()
                .start();


        System.out.println("WEB server is up! http://localhost:" + server.port() + "/simple-greet");

    }


    /**
     * Updates HTTP Routing.
     */
    static void routing(HttpRouting.Builder routing) {
        routing
               .register("/greet", new GreetService())
               .get("/simple-greet", (req, res) -> res.send("Hello World!")); 
    }
}