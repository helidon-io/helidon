/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.OidcCookieHandler;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webserver.ResponseHeaders;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.cors.CrossOriginConfig;

import jakarta.json.JsonObject;

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
 * <table class="config">
 * <caption>Configuration parameters</caption>
 * <tr>
 *     <th>key</th>
 *     <th>default value</th>
 *     <th>description</th>
 * </tr>
 * <tr>
 *     <td>redirect-uri</td>
 *     <td>/oidc/redirect</td>
 *     <td>Context root under which redirection endpoint is located (sent here by
 *              OIDC server</td>
 * </tr>
 * <tr>
 *     <td>oidc-metadata-type</td>
 *     <td>WELL_KNOWN</td>
 *     <td>How to obtain OIDC metadata. Can be WELL_KNOWN, URI, PATH or
 *          NONE</td>
 * </tr>
 * <tr>
 *     <td>oidc-metadata-uri</td>
 *     <td>N/A</td>
 *     <td>URI of the metadata if type set to URI</td>
 * </tr>
 * <tr>
 *     <td>oidc-metadata-path</td>
 *     <td>N/A</td>
 *     <td>Path on the filesystem if type set to PATH</td>
 * </tr>
 * <tr>
 *     <td>token-endpoint-type</td>
 *     <td>WELL_KNOWN</td>
 *     <td>Where is the token endpoint? WELL_KNOWN reads the location from OIDC
 *       Metadata</td>
 * </tr>
 * <tr>
 *     <td>token-endpoint-uri</td>
 *     <td>N/A</td>
 *     <td>URI of the token endpoint if type set to URI</td>
 * </tr>
 * <tr>
 *     <td>cookie-use</td>
 *     <td>true</td>
 *     <td>Whether to use cookie to provide the token to subsequent requests</td>
 * </tr>
 * <tr>
 *     <td>cookie-name</td>
 *     <td>OIDCTOKEN</td>
 *     <td>Name of the cookie to set (and expect)</td>
 * </tr>
 * <tr>
 *     <td>query-param-use</td>
 *     <td>false</td>
 *     <td>Whether to use query parameter to add to the request when redirecting to
 *              original URI</td></tr>
 * <tr>
 *     <td>query-param-name</td>
 *     <td>accessToken</td>
 *     <td>Name of the query parameter to set (and expect)</td>
 * </tr>
 * </table>
 */
public final class OidcSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(OidcSupport.class.getName());
    private static final String CODE_PARAM_NAME = "code";
    private static final String STATE_PARAM_NAME = "state";
    private static final String DEFAULT_REDIRECT = "/index.html";

    private final OidcConfig oidcConfig;
    private final OidcCookieHandler tokenCookieHandler;
    private final OidcCookieHandler idTokenCookieHandler;
    private final boolean enabled;
    private final CorsSupport corsSupport;

    private OidcSupport(Builder builder) {
        this.oidcConfig = builder.oidcConfig;
        this.enabled = builder.enabled;
        this.tokenCookieHandler = oidcConfig.tokenCookieHandler();
        this.idTokenCookieHandler = oidcConfig.idTokenCookieHandler();
        this.corsSupport = prepareCrossOriginSupport(oidcConfig.redirectUri(), oidcConfig.crossOriginConfig());
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
        return builder()
                .config(config, providerName)
                .build();
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
        return builder()
                .config(config, OidcProviderService.PROVIDER_CONFIG_KEY)
                .build();
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
        return builder()
                .config(oidcConfig)
                .build();
    }

    /**
     * A new builder instance to configure OIDC support.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void update(Routing.Rules rules) {
        if (enabled) {
            if (corsSupport != null) {
                rules.any(oidcConfig.redirectUri(), corsSupport);
            }
            rules.get(oidcConfig.redirectUri(), this::processOidcRedirect);
            if (oidcConfig.logoutEnabled()) {
                if (corsSupport != null) {
                    rules.any(oidcConfig.logoutUri(), corsSupport);
                }
                rules.get(oidcConfig.logoutUri(), this::processLogout);
            }
            rules.any(this::addRequestAsHeader);
        }
    }

    private void processLogout(ServerRequest req, ServerResponse res) {
        Optional<String> idTokenCookie = req.headers()
                .cookies()
                .first(idTokenCookieHandler.cookieName());

        if (idTokenCookie.isEmpty()) {
            LOGGER.finest("Logout request invoked without ID Token cookie");
            res.status(Http.Status.FORBIDDEN_403)
                    .send();
            return;
        }

        String encryptedIdToken = idTokenCookie.get();

        idTokenCookieHandler.decrypt(encryptedIdToken)
                .forSingle(idToken -> {
                    StringBuilder sb = new StringBuilder(oidcConfig.logoutEndpointUri()
                                                                 + "?id_token_hint="
                                                                 + idToken
                                                                 + "&post_logout_redirect_uri=" + postLogoutUri(req));

                    req.queryParams().first("state")
                            .ifPresent(it -> sb.append("&state=").append(it));

                    ResponseHeaders headers = res.headers();
                    headers.addCookie(tokenCookieHandler.removeCookie().build());
                    headers.addCookie(idTokenCookieHandler.removeCookie().build());

                    res.status(Http.Status.TEMPORARY_REDIRECT_307)
                            .addHeader(Http.Header.LOCATION, sb.toString())
                            .send();
                })
                .exceptionallyAccept(t -> sendError(res, t));
    }

    private void addRequestAsHeader(ServerRequest req, ServerResponse res) {
        //noinspection unchecked
        Map<String, List<String>> newHeaders = req.context()
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
                           List.of(req.uri().getPath()));
        } else {
            newHeaders.put(Security.HEADER_ORIG_URI,
                           List.of(req.uri().getPath() + "?" + query));
        }

        req.next();
    }

    private void processOidcRedirect(ServerRequest req, ServerResponse res) {
        // redirected from OIDC provider
        Optional<String> codeParam = req.queryParams().first(CODE_PARAM_NAME);
        // if code is not in the request, this is a problem
        codeParam.ifPresentOrElse(code -> processCode(code, req, res),
                                  () -> processError(req, res));
    }

    private void processCode(String code, ServerRequest req, ServerResponse res) {
        WebClient webClient = oidcConfig.appWebClient();

        FormParams.Builder form = FormParams.builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri(req));

        WebClientRequestBuilder post = webClient.post()
                .uri(oidcConfig.tokenEndpointUri())
                .accept(io.helidon.common.http.MediaType.APPLICATION_JSON);

        oidcConfig.updateRequest(OidcConfig.RequestType.CODE_TO_TOKEN,
                                 post,
                                 form);

        OidcConfig.postJsonResponse(post,
                                    form.build(),
                                    json -> processJsonResponse(req, res, json),
                                    (status, errorEntity) -> processError(res, status, errorEntity),
                                    (t, message) -> processError(res, t, message))
                .ignoreElement();

    }

    private Object postLogoutUri(ServerRequest req) {
        URI uri = oidcConfig.postLogoutUri();
        if (uri.getHost() != null) {
            return uri.toString();
        }
        String path = uri.getPath();
        path = path.startsWith("/") ? path : "/" + path;
        Optional<String> host = req.headers().first("host");
        if (host.isPresent()) {
            String scheme = req.isSecure() ? "https" : "http";
            return scheme + "://" + host.get() + path;
        } else {
            LOGGER.warning("Request without Host header received, yet post logout URI does not define a host");
            return oidcConfig.toString();
        }
    }

    private String redirectUri(ServerRequest req) {
        Optional<String> host = req.headers().first("host");

        if (host.isPresent()) {
            String scheme = req.isSecure() ? "https" : "http";
            return oidcConfig.redirectUriWithHost(scheme + "://" + host.get());
        } else {
            return oidcConfig.redirectUriWithHost();
        }
    }

    private String processJsonResponse(ServerRequest req, ServerResponse res, JsonObject json) {
        String tokenValue = json.getString("access_token");
        String idToken = json.getString("id_token", null);

        //redirect to "state"
        String state = req.queryParams().first(STATE_PARAM_NAME).orElse(DEFAULT_REDIRECT);
        res.status(Http.Status.TEMPORARY_REDIRECT_307);
        if (oidcConfig.useParam()) {
            state = (state.contains("?") ? "&" : "?") + oidcConfig.paramName() + "=" + tokenValue;
        }

        state = increaseRedirectCounter(state);
        res.headers().add(Http.Header.LOCATION, state);

        if (oidcConfig.useCookie()) {
            ResponseHeaders headers = res.headers();

            tokenCookieHandler.createCookie(tokenValue)
                    .forSingle(builder -> {
                        headers.addCookie(builder.build());
                        if (idToken != null && oidcConfig.logoutEnabled()) {
                            idTokenCookieHandler.createCookie(idToken)
                                    .forSingle(it -> {
                                        headers.addCookie(it.build());
                                        res.send();
                                    })
                                    .exceptionallyAccept(t -> sendError(res, t));
                        } else {
                            res.send();
                        }
                    })
                    .exceptionallyAccept(t -> sendError(res, t));
        } else {
            res.send();
        }

        return "done";
    }

    private void sendError(ServerResponse response, Throwable t) {
        // we cannot send the response back, as we may expose information about internal workings
        // of the security of this service
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Failed to process OIDC request", t);
        }
        response.status(Http.Status.INTERNAL_SERVER_ERROR_500)
                .send();
    }

    private Optional<String> processError(ServerResponse serverResponse, Http.ResponseStatus status, String entity) {
        LOGGER.log(Level.FINE,
                   "Invalid token or failed request when connecting to OIDC Token Endpoint. Response: " + entity
                           + ", response status: " + status);

        sendErrorResponse(serverResponse);
        return Optional.empty();
    }

    private Optional<String> processError(ServerResponse res, Throwable t, String message) {
        LOGGER.log(Level.FINE, message, t);

        sendErrorResponse(res);
        return Optional.empty();
    }

    // this must always be the same, so clients cannot guess what kind of problem they are facing
    // if they try to provide wrong data
    private void sendErrorResponse(ServerResponse serverResponse) {
        serverResponse.status(Http.Status.UNAUTHORIZED_401);
        serverResponse.send("Not a valid authorization code");
    }

    String increaseRedirectCounter(String state) {
        if (state.contains("?")) {
            // there are parameters
            Pattern attemptPattern = Pattern.compile(".*?(" + oidcConfig.redirectAttemptParam() + "=\\d+).*");
            Matcher matcher = attemptPattern.matcher(state);
            if (matcher.matches()) {
                String attempts = matcher.group(1);
                int equals = attempts.lastIndexOf('=');
                String count = attempts.substring(equals + 1);
                int countNumber = Integer.parseInt(count);
                countNumber++;
                return state.replace(attempts, oidcConfig.redirectAttemptParam() + "=" + countNumber);
            } else {
                return state + "&" + oidcConfig.redirectAttemptParam() + "=1";
            }
        } else {
            // no parameters
            return state + "?" + oidcConfig.redirectAttemptParam() + "=1";
        }
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

    private CorsSupport prepareCrossOriginSupport(String path, CrossOriginConfig crossOriginConfig) {
        return crossOriginConfig == null
                ? null
                : CorsSupport.builder()
                        .addCrossOrigin(path, crossOriginConfig)
                        .build();
    }

    /**
     * A fluent API builder for {@link io.helidon.security.providers.oidc.OidcSupport}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, OidcSupport> {
        private boolean enabled = true;
        private OidcConfig oidcConfig;

        private Builder() {
        }

        private static Config findMyKey(Config rootConfig, String providerName) {
            if (rootConfig.key().name().equals(providerName)) {
                return rootConfig;
            }

            return rootConfig.get("security.providers")
                    .asNodeList()
                    .get()
                    .stream()
                    .filter(it -> it.get(providerName).exists())
                    .findFirst()
                    .map(it -> it.get(providerName))
                    .orElseThrow(() -> new SecurityException("No configuration found for provider named: " + providerName));
        }

        @Override
        public OidcSupport build() {
            if (enabled && (oidcConfig == null)) {
                throw new IllegalStateException("When OIDC and security is enabled, OIDC configuration must be provided");
            }
            return new OidcSupport(this);
        }

        /**
         * Config located at the provider's key to read {@link io.helidon.security.providers.oidc.common.OidcConfig}.
         *
         * @param config configuration at the node of the provider
         * @return updated builder instance
         */
        public Builder config(Config config) {
            // also add support for `enabled` key in the `oidc` specific config
            config.get("enabled").asBoolean().ifPresent(this::enabled);

            if (enabled) {
                this.oidcConfig = OidcConfig.create(config);
            }
            return this;
        }

        /**
         * Use the provided {@link io.helidon.security.providers.oidc.common.OidcConfig} for this builder.
         *
         * @param config OIDC configuration to use
         * @return updated builder instance
         */
        public Builder config(OidcConfig config) {
            this.oidcConfig = config;
            return this;
        }

        /**
         * Config located either at the configuration root, or at the provider node.
         *
         * @param config configuration to use
         * @param providerName name of the security provider used for the {@link io.helidon.security.providers.oidc.OidcSupport}
         *                     configuration
         * @return updated builder instance
         */
        public Builder config(Config config, String providerName) {
            // if this is root config, we need to honor `security.enabled`
            config.get("security.enabled").asBoolean().ifPresent(this::enabled);

            config(findMyKey(config, providerName));
            return this;
        }

        /**
         * You can disable the OIDC support in case it should not be used.
         * This can also be achieved through configuration, by setting {@code security.enabled} to {@code false}
         * when using root configuration, or by setting {@code enabled} to {@code false} when using provider configuration node.
         *
         * @param enabled whether the support should be enabled or not
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }
}
