/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

import java.io.StringReader;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weight;
import io.helidon.common.configurable.LruCache;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.parameters.Parameters;
import io.helidon.config.Config;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.ServerResponseHeaders;
import io.helidon.http.Status;
import io.helidon.security.Security;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.OidcCookieHandler;
import io.helidon.security.providers.oidc.common.Tenant;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.security.providers.oidc.common.spi.TenantConfigFinder;
import io.helidon.security.providers.oidc.common.spi.TenantConfigProvider;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.security.SecurityHttpFeature;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;

import static io.helidon.http.HeaderNames.HOST;
import static io.helidon.security.providers.oidc.common.spi.TenantConfigFinder.DEFAULT_TENANT_ID;

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
@Weight(800)
public final class OidcFeature implements HttpFeature {
    static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(Map.of());
    static final JsonBuilderFactory JSON_BUILDER_FACTORY = Json.createBuilderFactory(Map.of());
    private static final System.Logger LOGGER = System.getLogger(OidcFeature.class.getName());
    private static final String CODE_PARAM_NAME = "code";
    private static final String STATE_PARAM_NAME = "state";
    private static final String DEFAULT_REDIRECT = "/index.html";

    private final List<TenantConfigFinder> oidcConfigFinders;
    private final LruCache<String, Tenant> tenants = LruCache.create();
    private final OidcConfig oidcConfig;
    private final OidcCookieHandler tokenCookieHandler;
    private final OidcCookieHandler idTokenCookieHandler;
    private final OidcCookieHandler refreshTokenCookieHandler;
    private final OidcCookieHandler tenantCookieHandler;
    private final OidcCookieHandler stateCookieHandler;
    private final boolean enabled;
    private final CorsSupport corsSupport;

    private OidcFeature(Builder builder) {
        this.oidcConfig = builder.oidcConfig;
        this.enabled = builder.enabled;
        if (enabled) {
            this.tokenCookieHandler = oidcConfig.tokenCookieHandler();
            this.idTokenCookieHandler = oidcConfig.idTokenCookieHandler();
            this.refreshTokenCookieHandler = oidcConfig.refreshTokenCookieHandler();
            this.tenantCookieHandler = oidcConfig.tenantCookieHandler();
            this.stateCookieHandler = oidcConfig.stateCookieHandler();
            this.corsSupport = prepareCrossOriginSupport(oidcConfig.redirectUri(), oidcConfig.crossOriginConfig());
            this.oidcConfigFinders = List.copyOf(builder.tenantConfigFinders);
            this.oidcConfigFinders.forEach(tenantConfigFinder -> tenantConfigFinder.onChange(tenants::remove));
        } else {
            this.tokenCookieHandler = null;
            this.idTokenCookieHandler = null;
            this.refreshTokenCookieHandler = null;
            this.tenantCookieHandler = null;
            this.stateCookieHandler = null;
            this.corsSupport = null;
            this.oidcConfigFinders = List.of();
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
    public static OidcFeature create(Config config, String providerName) {
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
    public static OidcFeature create(Config config) {
        return builder()
                .config(config, OidcProviderService.PROVIDER_CONFIG_KEY)
                .build();
    }

    /**
     * Load OIDC support for webserver from {@link io.helidon.security.providers.oidc.common.OidcConfig} instance.
     * When programmatically configuring your environment, this is the best approach, to share configuration
     * between this class and {@link OidcProvider}.
     *
     * @param oidcConfig configuration of OIDC integration
     * @return OIDC webserver integration based on the configuration
     */
    public static OidcFeature create(OidcConfig oidcConfig) {
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
    public void setup(HttpRouting.Builder routing) {
        if (enabled) {
            if (corsSupport != null) {
                routing.any(oidcConfig.redirectUri(), corsSupport);
            }
            routing.get(oidcConfig.redirectUri(), this::processOidcRedirect);
            if (oidcConfig.logoutEnabled()) {
                if (corsSupport != null) {
                    routing.any(oidcConfig.logoutUri(), corsSupport);
                }
                routing.get(oidcConfig.logoutUri(), this::processLogout);
            }
            routing.any(this::addRequestAsHeader);
        }
    }

    private void processLogout(ServerRequest req, ServerResponse res) {
        String tenantName = findTenantName(req);
        processTenantLogout(req, res, tenantName);
    }

    private String findTenantName(ServerRequest request) {
        List<String> missingLocations = new LinkedList<>();
        OptionalValue<String> tenantId = null;
        if (oidcConfig.useParam()) {
            tenantId = request.query().first(oidcConfig.tenantParamName());

            if (tenantId.isEmpty()) {
                missingLocations.add("query-param");
            }
        }
        if (oidcConfig.useCookie() && tenantId == null) {
            Optional<String> cookie = oidcConfig.tenantCookieHandler()
                    .findCookie(request.headers().toMap());

            if (cookie.isPresent()) {
                return cookie.get();
            }
            missingLocations.add("cookie");
        }
        if (tenantId != null) {
            return tenantId.get();
        } else {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "Missing tenant id, could not find in either of: " + missingLocations
                                      + "Falling back to the default tenant id: " + DEFAULT_TENANT_ID);
            }
            return DEFAULT_TENANT_ID;
        }
    }

    private void processTenantLogout(ServerRequest req, ServerResponse res, String tenantName) {
        Tenant tenant = obtainCurrentTenant(tenantName);

        logoutWithTenant(req, res, tenant);
    }

    private Tenant obtainCurrentTenant(String tenantName) {
        Optional<Tenant> maybeTenant = tenants.get(tenantName);
        if (maybeTenant.isPresent()) {
            return maybeTenant.get();
        } else {
            Tenant tenant = oidcConfigFinders.stream()
                    .map(finder -> finder.config(tenantName))
                    .flatMap(Optional::stream)
                    .map(tenantConfig -> Tenant.create(oidcConfig, tenantConfig))
                    .findFirst()
                    .orElseGet(() -> Tenant.create(oidcConfig, oidcConfig.tenantConfig(tenantName)));
            return tenants.computeValue(tenantName, () -> Optional.of(tenant)).get();
        }
    }

    private void logoutWithTenant(ServerRequest req, ServerResponse res, Tenant tenant) {
        OptionalValue<String> idTokenCookie = req.headers()
                .cookies()
                .first(idTokenCookieHandler.cookieName());

        if (idTokenCookie.isEmpty()) {
            LOGGER.log(Level.TRACE, "Logout request invoked without ID Token cookie");
            res.status(Status.FORBIDDEN_403)
                    .send();
            return;
        }

        String encryptedIdToken = idTokenCookie.get();

        try {
            String idToken = idTokenCookieHandler.decrypt(encryptedIdToken);
            StringBuilder sb = new StringBuilder(tenant.logoutEndpointUri()
                                                         + "?id_token_hint="
                                                         + idToken
                                                         + "&post_logout_redirect_uri=" + postLogoutUri(req));

            req.query().first("state")
                    .ifPresent(it -> sb.append("&state=").append(it));

            ServerResponseHeaders headers = res.headers();
            headers.addCookie(tokenCookieHandler.removeCookie().build());
            headers.addCookie(idTokenCookieHandler.removeCookie().build());
            headers.addCookie(tenantCookieHandler.removeCookie().build());
            headers.addCookie(refreshTokenCookieHandler.removeCookie().build());

            res.status(Status.TEMPORARY_REDIRECT_307)
                    .header(HeaderNames.LOCATION, sb.toString())
                    .send();
        } catch (Exception e) {
            sendError(res, e);
        }
    }

    private void addRequestAsHeader(ServerRequest req, ServerResponse res) {
        //noinspection unchecked
        Context context = Contexts.context().orElseThrow(() -> new SecurityException("Context must be available"));

        Map<String, List<String>> newHeaders = context
                .get(SecurityHttpFeature.CONTEXT_ADD_HEADERS, Map.class)
                .map(theMap -> (Map<String, List<String>>) theMap)
                .orElseGet(() -> {
                    Map<String, List<String>> newMap = new HashMap<>();
                    context.register(SecurityHttpFeature.CONTEXT_ADD_HEADERS, newMap);
                    return newMap;
                });

        String query = req.query().rawValue();
        if (query.isEmpty()) {
            newHeaders.put(Security.HEADER_ORIG_URI,
                           List.of(req.path().rawPath()));
        } else {
            newHeaders.put(Security.HEADER_ORIG_URI,
                           List.of(req.path().rawPath() + "?" + query));
        }

        res.next();
    }

    private void processOidcRedirect(ServerRequest req, ServerResponse res) {
        // redirected from OIDC provider
        OptionalValue<String> codeParam = req.query().first(CODE_PARAM_NAME);
        // if code is not in the request, this is a problem
        codeParam.ifPresentOrElse(code -> processCode(code, req, res),
                                  () -> processError(req, res));
    }

    private void processCode(String code, ServerRequest req, ServerResponse res) {
        String tenantName = req.query().first(oidcConfig.tenantParamName()).orElse(TenantConfigFinder.DEFAULT_TENANT_ID);
        Tenant tenant = obtainCurrentTenant(tenantName);

        processCodeWithTenant(code, req, res, tenantName, tenant);
    }

    private void processCodeWithTenant(String code, ServerRequest req, ServerResponse res, String tenantName, Tenant tenant) {
        Optional<String> maybeStateCookie = stateCookieHandler.findCookie(req.headers().toMap());
        if (maybeStateCookie.isEmpty()) {
            processError(res,
                         Status.UNAUTHORIZED_401,
                         "State cookie needs to be provided upon redirect");
            return;
        }
        String stateBase64 = new String(Base64.getDecoder().decode(maybeStateCookie.get()), StandardCharsets.UTF_8);
        JsonObject stateCookie = JSON_READER_FACTORY.createReader(new StringReader(stateBase64)).readObject();
        //Remove state cookie
        res.headers().addCookie(stateCookieHandler.removeCookie().build());
        String state = stateCookie.getString("state");
        String queryState = req.query().get("state");
        if (!state.equals(queryState)) {
            processError(res,
                         Status.UNAUTHORIZED_401,
                         "State of the original request and obtained from identity server does not match");
            return;
        }

        TenantConfig tenantConfig = tenant.tenantConfig();

        WebClient webClient = tenant.appWebClient();

        Parameters.Builder form = Parameters.builder("oidc-form-params")
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri(req, tenantName));

        HttpClientRequest post = webClient.post()
                .uri(tenant.tokenEndpointUri())
                .header(HeaderValues.ACCEPT_JSON);

        OidcUtil.updateRequest(OidcConfig.RequestType.CODE_TO_TOKEN, tenantConfig, form);

        try (HttpClientResponse response = post.submit(form.build())) {
            if (response.status().family() == Status.Family.SUCCESSFUL) {
                try {
                    JsonObject jsonObject = response.as(JsonObject.class);
                    processJsonResponse(req, res, jsonObject, tenantName, stateCookie);
                } catch (Exception e) {
                    processError(res, e, "Failed to read JSON from response");
                }
            } else {
                String message;
                try {
                    message = response.as(String.class);
                } catch (Exception e) {
                    processError(res, e, "Failed to process error entity");
                    return;
                }
                try {
                    processError(res, response.status(), message);
                } catch (Exception e) {
                    throw new SecurityException("Failed to process request: " + message);
                }
            }
        } catch (Exception e) {
            processError(res, e, "Failed to invoke request");
        }
    }

    private Object postLogoutUri(ServerRequest req) {
        URI uri = oidcConfig.postLogoutUri();
        if (uri.getHost() != null) {
            return uri.toString();
        }
        String path = uri.getPath();
        path = path.startsWith("/") ? path : "/" + path;
        ServerRequestHeaders headers = req.headers();
        if (headers.contains(HOST)) {
            String scheme = oidcConfig.forceHttpsRedirects() || req.isSecure() ? "https" : "http";
            return scheme + "://" + headers.get(HOST).get() + path;
        } else {
            LOGGER.log(Level.WARNING, "Request without Host header received, yet post logout URI does not define a host");
            return oidcConfig.toString();
        }
    }

    private String redirectUri(ServerRequest req, String tenantName) {
        Optional<String> host = req.headers().first(HOST);
        String uri;

        if (host.isPresent()) {
            String scheme = req.isSecure() ? "https" : "http";
            uri = oidcConfig.redirectUriWithHost(scheme + "://" + host.get());
        } else {
            uri = oidcConfig.redirectUriWithHost();
        }
        if (!DEFAULT_TENANT_ID.equals(tenantName)) {
            return uri + (uri.contains("?") ? "&" : "?") + encode(oidcConfig.tenantParamName()) + "=" + encode(tenantName);
        }
        return uri;
    }

    private String processJsonResponse(ServerRequest req,
                                       ServerResponse res,
                                       JsonObject json,
                                       String tenantName,
                                       JsonObject stateCookie) {
        String accessToken = json.getString("access_token");
        String idToken = json.getString("id_token", null);
        String refreshToken = json.getString("refresh_token", null);

        Jwt idTokenJwt = SignedJwt.parseToken(idToken).getJwt();
        String nonceOriginal = stateCookie.getString("nonce");
        String nonceAccess = idTokenJwt.nonce()
                .orElseThrow(() -> new IllegalStateException("Nonce is required to be present in the id token"));
        if (!nonceAccess.equals(nonceOriginal)) {
            throw new IllegalStateException("Original nonce and the one obtained from id token does not match");
        }

        //redirect to "originalUri"
        String originalUri = stateCookie.getString("originalUri", DEFAULT_REDIRECT);
        res.status(Status.TEMPORARY_REDIRECT_307);
        if (oidcConfig.useParam()) {
            originalUri += (originalUri.contains("?") ? "&" : "?") + encode(oidcConfig.paramName()) + "=" + accessToken;
            if (idToken != null) {
                originalUri += "&" + encode(oidcConfig.idTokenParamName()) + "=" + idToken;
            }
            if (!DEFAULT_TENANT_ID.equals(tenantName)) {
                originalUri += "&" + encode(oidcConfig.tenantParamName()) + "=" + encode(tenantName);
            }
        }

        originalUri = increaseRedirectCounter(originalUri);
        res.headers().add(HeaderNames.LOCATION, originalUri);

        if (oidcConfig.useCookie()) {
            try {
                JsonObject accessTokenJson = JSON_BUILDER_FACTORY.createObjectBuilder()
                        .add("accessToken", accessToken)
                        .add("remotePeer", req.remotePeer().host())
                        .build();
                String encodedAccessToken = Base64.getEncoder()
                        .encodeToString(accessTokenJson.toString().getBytes(StandardCharsets.UTF_8));

                ServerResponseHeaders headers = res.headers();

                OidcCookieHandler tenantCookieHandler = oidcConfig.tenantCookieHandler();

                headers.addCookie(tenantCookieHandler.createCookie(tenantName).build()); //Add tenant name cookie
                headers.addCookie(tokenCookieHandler.createCookie(encodedAccessToken).build());  //Add token cookie
                if (refreshToken != null) {
                    headers.addCookie(refreshTokenCookieHandler.createCookie(refreshToken).build());  //Add refresh token cookie
                }

                if (idToken != null) {
                    headers.addCookie(idTokenCookieHandler.createCookie(idToken).build());  //Add token id cookie
                }
                res.send();

            } catch (Exception e) {
                sendError(res, e);
            }
        } else {
            res.send();
        }

        return "done";
    }

    private String encode(String toEncode) {
        return URLEncoder.encode(toEncode, StandardCharsets.UTF_8);
    }

    private void sendError(ServerResponse response, Throwable t) {
        // we cannot send the response back, as we may expose information about internal workings
        // of the security of this service
        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "Failed to process OIDC request", t);
        }
        response.status(Status.INTERNAL_SERVER_ERROR_500)
                .send();
    }

    private Optional<String> processError(ServerResponse serverResponse, Status status, String entity) {
        LOGGER.log(Level.DEBUG,
                   "Invalid token or failed request when connecting to OIDC Token Endpoint. Response: " + entity
                           + ", response status: " + status);

        sendErrorResponse(serverResponse);
        return Optional.empty();
    }

    private Optional<String> processError(ServerResponse res, Throwable t, String message) {
        LOGGER.log(Level.DEBUG, message, t);

        sendErrorResponse(res);
        return Optional.empty();
    }

    // this must always be the same, so clients cannot guess what kind of problem they are facing
    // if they try to provide wrong data
    private void sendErrorResponse(ServerResponse serverResponse) {
        serverResponse.status(Status.UNAUTHORIZED_401);
        serverResponse.send("Not a valid authorization code2");
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
        String error = req.query().first("error").orElse("invalid_request");
        String errorDescription = req.query().first("error_description")
                .orElseGet(() -> "Failed to process authorization request. Expected redirect from OIDC server with code"
                        + " parameter, but got: " + req.query());
        LOGGER.log(Level.WARNING,
                   () -> "Received request on OIDC endpoint with no code. Error: "
                           + error
                           + " Error description: "
                           + errorDescription);

        res.status(Status.BAD_REQUEST_400);
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
     * A fluent API builder for {@link OidcFeature}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, OidcFeature> {

        private static final int BUILDER_WEIGHT = 50000;
        private static final int DEFAULT_WEIGHT = 100000;

        private final HelidonServiceLoader.Builder<TenantConfigProvider> tenantConfigProviders = HelidonServiceLoader
                .builder(ServiceLoader.load(TenantConfigProvider.class))
                .defaultWeight(DEFAULT_WEIGHT);
        private boolean enabled = true;
        private Config config = Config.empty();
        private OidcConfig oidcConfig;
        private List<TenantConfigFinder> tenantConfigFinders;

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
        public OidcFeature build() {
            if (enabled && (oidcConfig == null)) {
                throw new IllegalStateException("When OIDC and security is enabled, OIDC configuration must be provided");
            }
            tenantConfigFinders = tenantConfigProviders.build().asList().stream()
                    .map(provider -> provider.createTenantConfigFinder(config))
                    .collect(Collectors.toList());
            return new OidcFeature(this);
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
         * @param providerName name of the security provider used for the {@link OidcFeature}
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

        /**
         * Whether to allow {@link TenantConfigProvider} service loader discovery.
         * Default value is {@code true}.
         *
         * @param discoverConfigProviders whether to use service loader
         * @return updated builder instance
         */
        public Builder discoverTenantConfigProviders(boolean discoverConfigProviders) {
            tenantConfigProviders.useSystemServiceLoader(discoverConfigProviders);
            return this;
        }

        /**
         * Add specific {@link TenantConfigFinder} implementation.
         * Priority {@link #BUILDER_WEIGHT} is used.
         *
         * @param configFinder config finder implementation
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantConfigFinder configFinder) {
            return addTenantConfigFinder(configFinder, BUILDER_WEIGHT);
        }

        /**
         * Add specific {@link TenantConfigFinder} implementation with specific priority.
         *
         * @param configFinder config finder implementation
         * @param priority finder priority
         * @return updated builder instance
         */
        public Builder addTenantConfigFinder(TenantConfigFinder configFinder, int priority) {
            tenantConfigProviders.addService(config -> configFinder, priority);
            return this;
        }
    }
}
