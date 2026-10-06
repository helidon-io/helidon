/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * OIDC integration requires web resources to be exposed through a web server.
 * This registers the endpoint to which OIDC redirects browser after successful login.
 * Creating this support requires a JVM cryptography policy permitting AES-256.
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
 *     <td>Whether to add encrypted access token handoff to the query when redirecting to the original URI</td></tr>
 * <tr>
 *     <td>query-param-name</td>
 *     <td>accessToken</td>
 *     <td>Name of the query parameter to set with encrypted handoff (and expect)</td>
 * </tr>
 * <tr>
 *     <td>legacy-state-param</td>
 *     <td>false</td>
 *     <td>Whether to write and accept the legacy raw local redirect URI in OIDC state during rolling updates.</td>
 * </tr>
 * <tr>
 *     <td>legacy-state-fallback</td>
 *     <td>false</td>
 *     <td>Whether to accept older unbound encrypted or raw local state after strict validation fails.</td>
 * </tr>
 * <tr>
 *     <td>legacy-query-param-handoff</td>
 *     <td>false</td>
 *     <td>Whether to write raw access tokens to callback redirect queries during rolling updates.</td>
 * </tr>
 * </table>
 */
public final class OidcSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(OidcSupport.class.getName());
    private static final String CODE_PARAM_NAME = "code";
    private static final String STATE_PARAM_NAME = "state";

    private final OidcConfig oidcConfig;

    private OidcSupport(OidcConfig oidcConfig) {
        OidcState.validateCryptoSupport();
        this.oidcConfig = oidcConfig;
        if (oidcConfig.useParam() && !oidcConfig.useCookie() && !oidcConfig.legacyQueryParamHandoff()) {
            LOGGER.warning("OIDC query parameter handoff is enabled without cookies. When possible, enable cookie-use "
                                   + "to bind the handoff to the browser session.");
        }
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
        rules.get(oidcConfig.redirectUri(), this::processOidcRedirect)
             .any(this::addRequestAsHeader);
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
        String path = req.uri().getRawPath();
        path = path == null || path.isEmpty() ? "/" : path;
        if ((null == query) || query.isEmpty()) {
            newHeaders.put(Security.HEADER_ORIG_URI,
                           CollectionsHelper.listOf(path));
        } else {
            newHeaders.put(Security.HEADER_ORIG_URI,
                           CollectionsHelper.listOf(path + "?" + query));
        }

        req.next();
    }

    private void processOidcRedirect(ServerRequest req, ServerResponse res) {
        // redirected from IDCS
        Optional<String> codeParam = req.queryParams().first(CODE_PARAM_NAME);
        // if code is not in the request, this is a problem
        OptionalHelper.from(codeParam)
                .ifPresentOrElse(code -> processCode(code, req, res), () -> processError(req, res));
    }

    private void processCode(String code, ServerRequest req, ServerResponse res) {
        Optional<String> stateNonce = req.headers().cookies()
                .first(OidcState.loginStateNonceCookieName(oidcConfig.cookieName()));
        Optional<String> originalUri = req.queryParams().first(STATE_PARAM_NAME)
                .flatMap(state -> OptionalHelper.from(OidcState.loginRedirect(state, oidcConfig, stateNonce, true))
                        .or(() -> oidcConfig.legacyStateFallback()
                                ? OidcState.loginRedirect(state, oidcConfig, stateNonce, false)
                                : Optional.empty())
                        .or(() -> oidcConfig.legacyStateParam() || oidcConfig.legacyStateFallback()
                                ? OidcState.localRedirectUri(state)
                                : Optional.empty())
                        .asOptional());
        if (!originalUri.isPresent()) {
            res.status(Http.Status.UNAUTHORIZED_401);
            res.send("Not a valid authorization code");
            return;
        }
        if (stateNonce.isPresent()) {
            res.headers().add(Http.Header.SET_COOKIE,
                              OidcState.loginStateNonceRemoveCookie(oidcConfig.cookieName(),
                                                                   oidcConfig.redirectUri(),
                                                                   oidcConfig.cookieOptions()));
        }

        MultivaluedHashMap<String, String> formValues = new MultivaluedHashMap<>();
        formValues.putSingle("grant_type", "authorization_code");
        formValues.putSingle("code", code);
        formValues.putSingle("redirect_uri", oidcConfig.redirectUriWithHost());

        Response response = oidcConfig.tokenEndpoint().request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formValues));

        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonResponse = response.readEntity(JsonObject.class);
            String tokenValue = jsonResponse.getString("access_token");
            String state = originalUri.get();
            Optional<String> queryNonce = oidcConfig.useParam() && oidcConfig.useCookie()
                    && !oidcConfig.legacyQueryParamHandoff()
                    ? Optional.of(UUID.randomUUID().toString())
                    : Optional.empty();
            res.status(Http.Status.TEMPORARY_REDIRECT_307);
            if (oidcConfig.useParam()) {
                String handoff = oidcConfig.legacyQueryParamHandoff()
                        ? tokenValue
                        : queryNonce.map(nonce -> OidcState.createQueryResult(tokenValue, jsonResponse, oidcConfig, nonce))
                                .orElseGet(() -> OidcState.createQueryResult(tokenValue, jsonResponse, oidcConfig));
                state = state + (state.contains("?") ? "&" : "?") + encode(oidcConfig.paramName()) + "=" + encode(handoff);
            }

            state = increaseRedirectCounter(state);
            res.headers().add(Http.Header.LOCATION, state);

            if (oidcConfig.useCookie()) {
                queryNonce.ifPresent(nonce -> res.headers().add(Http.Header.SET_COOKIE,
                                                               OidcState.queryResultNonceSetCookie(oidcConfig.cookieName(),
                                                                                                   nonce,
                                                                                                   oidcConfig.cookieOptions())));
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
                .asNodeList()
                .get()
                .stream()
                .filter(it -> it.get(providerName).exists())
                .findFirst()
                .map(it -> it.get(providerName))
                .orElseThrow(() -> new SecurityException("No configuration found for provider named: " + providerName));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        }
    }

}
