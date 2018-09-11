/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.oidc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * OIDC integration requires web resources to be exposed through a web server.
 * This registers the endpoint to which OIDC redirects browser after successful login.
 *
 * This incorporates the "response_type=code" approach.
 *
 * When passing configuration to this class, you should pass the root of configuration
 * (that contains security.providers). This class then reads the configuration for provider
 * named "oidc" or (if mutliples are configured) for the name specified.
 * Configuration options used by this class are (under security.providers[].${name}):
 * <table border="1">
 * <caption>Configuration parameters</caption>
 * <tr><th>key</th><th>default value</th><th>description</th></tr>
 * <tr><td>redirect-uri</td><td>/oidc/redirect</td><td>Context root under which redirection endpoint is located (sent here by
 * OIDC server</td></tr>
 * <tr><td>oidc-metadata-type</td><td>WELL_KNOWN</td><td>How to obtain OIDC metadata. Can be WELL_KNOWN, URI, PATH or
 * NONE</td></tr>
 * <tr><td>oidc-metadata-uri</td><td>N/A</td><td>URI of the metadata if type set to URI</td></tr>
 * <tr><td>oidc-metadata-path</td><td>N/A</td><td>Path on the filesystem if type set to PATH</td></tr>
 * <tr><td>token-endpoint-type</td><td>WELL_KNOWN</td><td>Where is the token endpoint? WELL_KNOWN reads the location from OIDC
 * Metadata</td></tr>
 * <tr><td>token-endpoint-uri</td><td>N/A</td><td>URI of the token endpoint if type set to URI</td></tr>
 * <tr><td>cookie-use</td><td>true</td><td>Whether to use cookie to provide the token to subsequent requests</td></tr>
 * <tr><td>cookie-name</td><td>OIDCTOKEN</td><td>Name of the cookie to set (and expect)</td></tr>
 * <tr><td>query-param-use</td><td>false</td><td>Whether to use query parameter to add to the request when redirecting to
 * original URI</td></tr>
 * <tr><td>query-param-name</td><td>accessToken</td><td>Name of the query parameter to set (and expect)</td></tr>
 * </table>
 */
public final class OidcSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(OidcSupport.class.getName());
    private static final String CODE_PARAM_NAME = "code";
    private static final String STATE_PARAM_NAME = "state";
    private static final String DEFAULT_REDIRECT = "/index.html";

    private final OidcConfig oidcConfig;

    private OidcSupport(OidcConfig oidcConfig) {
        this.oidcConfig = oidcConfig;
    }

    /**
     * Load OIDC support for webserver from config. This works from two places in config tree -
     * either from root (expecting security.providers.providerName
     * under current key) or from the key itself (e.g. providerName is the current key).
     *
     * @param config       Config instance on expected node
     * @param providerName name of the node that contains OIDC configuration
     * @return OIDC webserver integration based on the config
     */
    public static OidcSupport create(Config config, String providerName) {
        return create(OidcConfig.create(findMyKey(config, providerName)));
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get(oidcConfig.redirectUri(), (req, res) -> {
            // redirected from IDCS
            Optional<String> codeParam = req.queryParams().first(CODE_PARAM_NAME);
            // if code is not in the request, this is a problem
            OptionalHelper.from(codeParam)
                    .ifPresentOrElse(code -> processCode(code, req, res), () -> processError(req, res));
        });

        rules.any((req, res) -> {
            //noinspection unchecked
            Map<String, List<String>> newHeaders =
                    req.context()
                            .get(WebSecurity.CONTEXT_ADD_HEADERS, Map.class)
                            .map(theMap -> (Map<String, List<String>>) theMap)
                            .orElseGet(() -> {
                                Map<String, List<String>> newMap = new HashMap<>();
                                req.context().register(WebSecurity.CONTEXT_ADD_HEADERS, newMap);
                                return newMap;
                            });

            String query = req.query();
            if ((null == query) || query.isEmpty()) {
                newHeaders.put(Security.HEADER_ORIG_URI,
                               CollectionsHelper.listOf(req.uri().getPath()));
            } else {
                newHeaders.put(Security.HEADER_ORIG_URI,
                               CollectionsHelper.listOf(req.uri().getPath() + "?" + query));
            }

            req.next();
        });
    }

    private void processCode(String code, ServerRequest req, ServerResponse res) {
        MultivaluedHashMap<String, String> formValues = new MultivaluedHashMap<>();
        formValues.putSingle("grant_type", "authorization_code");
        formValues.putSingle("code", code);

        Response response = oidcConfig.tokenEndpoint().request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formValues));

        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonResponse = response.readEntity(JsonObject.class);
            String tokenValue = jsonResponse.getString("access_token");
            //redirect to "state"
            String state = req.queryParams().first(STATE_PARAM_NAME).orElse(DEFAULT_REDIRECT);
            res.status(Http.Status.TEMPORARY_REDIRECT_307);
            if (oidcConfig.useParam()) {
                if (state.contains("?")) {
                    state = state + "&" + oidcConfig.paramName() + "=" + tokenValue;
                } else {
                    state = state + "?" + oidcConfig.paramName() + "=" + tokenValue;
                }
            }

            res.headers()
                    .add(Http.Header.LOCATION, state);

            if (oidcConfig.useCookie()) {
                res.headers()
                        .add("Set-Cookie", oidcConfig.cookieName() + "=" + tokenValue + oidcConfig.cookieOptions());
            }

            res.send();
        } else {
            String entity = response.readEntity(String.class);
            LOGGER.log(Level.FINE, "Invalid token or failed request when connecting to OIDC Token Endpoint. Response: " + entity);
            res.status(Http.Status.UNAUTHORIZED_401);
            res.send("Not a valid authorization code");
        }
    }

    /**
     * Load OIDC support for webserver from config. This works from two places in config tree -
     * either from root (expecting security.providers.{@value OidcProviderService#PROVIDER_CONFIG_KEY}
     * under current key) or from the provider's configuration.
     * (expecting OIDC keys directly under current key).
     *
     * @param config Config instance on expected node
     * @return OIDC webserver integration based on the config
     */
    public static OidcSupport create(Config config) {
        return create(config, OidcProviderService.PROVIDER_CONFIG_KEY);
    }

    private void processError(ServerRequest req, ServerResponse res) {
        String error = req.queryParams().first("error").orElse("invalid_request");
        String errorDescription = req.queryParams().first("error_description")
                .orElseGet(() -> "Failed to process authorization request. Expected redirect from OIDC server with code"
                        + " parameter, but got: " + req.query());
        LOGGER.log(Level.WARNING,
                   () -> "Received request on OIDC endpoint with no code. Error: "
                           + error
                           + " Error description: "
                           + errorDescription);

        res.status(Http.Status.BAD_REQUEST_400);
        res.send("{\"error\": \"" + error + "\", \"error_description\": \"" + errorDescription + "\"}");
    }

    /**
     * Load OIDC support for webserver from {@link OidcConfig} instance.
     * When programmatically configuring your environment, this is the best approach, to share configuration
     * between this class and {@link OidcProvider}.
     *
     * @param oidcConfig configuration of OIDC integration
     * @return OIDC webserver integration based on the configuration
     */
    public static OidcSupport create(OidcConfig oidcConfig) {
        return new OidcSupport(oidcConfig);
    }

    private static Config findMyKey(Config rootConfig, String providerName) {
        if (rootConfig.key().name().equals(providerName)) {
            return rootConfig;
        }

        return rootConfig.get("security.providers")
                .asList(Config.class)
                .stream()
                .filter(it -> it.get(providerName).exists())
                .findFirst()
                .map(it -> it.get(providerName))
                .orElseThrow(() -> new SecurityException("No configuration found for provider named: " + providerName));
    }

}
