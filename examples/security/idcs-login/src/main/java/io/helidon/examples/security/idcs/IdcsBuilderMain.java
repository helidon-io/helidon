/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.security.idcs;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.http.HttpMediaTypes;
import io.helidon.logging.common.LogConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider;
import io.helidon.security.providers.oidc.OidcFeature;
import io.helidon.security.providers.oidc.OidcProvider;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * IDCS Login example main class using configuration .
 */
@SuppressWarnings("HttpUrlsUsage")
public final class IdcsBuilderMain {

    // do not change this constant, unless you modify configuration
    // of IDCS application redirect URI
    static final int PORT = 7987;

    private IdcsBuilderMain() {
    }

    /**
     * Start the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        WebServerConfig.Builder builder = WebServer.builder();
        setup(builder);
        WebServer server = builder.build();

        long t = System.nanoTime();
        server.start();
        long time = System.nanoTime() - t;

        System.out.printf("""
                Server started in %2$d ms

                Started server on localhost:%1$d
                You can access this example at http://localhost:%1$d/rest/profile

                Check application.yaml in case you are behind a proxy to configure it
                """, server.port(), TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS));
    }

    static void setup(WebServerConfig.Builder server) {
        Config config = buildConfig();

        OidcConfig oidcConfig = OidcConfig.builder()
                .clientId("clientId.of.your.application")
                .clientSecret("clientSecret.of.your.application")
                .identityUri(URI.create(
                        "https://idcs-tenant-id.identity.oracle.com"))
                //.proxyHost("proxy.proxy.com")
                .frontendUri("http://your.host:your.port")
                // tell us it is IDCS, so we can modify the behavior
                .serverType("idcs")
                .build();

        Security security = Security.builder()
                .addProvider(OidcProvider.create(oidcConfig))
                .addProvider(IdcsRoleMapperProvider.builder()
                        .config(config)
                        .oidcConfig(oidcConfig))
                .build();
        // security needs to be available for other features, such as server security feature
        Contexts.globalContext().register(security);

        server.port(PORT)
                .routing(routing -> routing
                        // IDCS requires a web resource for redirects
                        .addFeature(OidcFeature.create(config))
                        // web server does not (yet) have possibility to configure routes in config files, so explicit...
                        .get("/rest/profile", (req, res) -> {
                            Optional<SecurityContext> securityContext = req.context().get(SecurityContext.class);
                            res.headers().contentType(HttpMediaTypes.PLAINTEXT_UTF_8);
                            res.send("Response from builder based service, you are: \n" + securityContext
                                    .flatMap(SecurityContext::user)
                                    .map(Subject::toString)
                                    .orElse("Security context is null"));
                        }));
    }

    private static Config buildConfig() {
        return Config.builder()
                     .sources(
                             // you can use this file to override the defaults built-in
                             file(System.getProperty("user.home") + "/helidon/conf/examples.yaml").optional(),
                             // in jar file (see src/main/resources/application.yaml)
                             classpath("application.yaml"))
                     .build();
    }
}
