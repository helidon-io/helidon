/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.JsonObject;

import io.helidon.common.Base64Value;
import io.helidon.common.context.Contexts;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webserver.ResponseHeaders;
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
    private final OidcConfig.CookieHandler cookieHandler;
    private final boolean enabled;
    private final Function<String, Single<String>> encryptor;
    private final Function<String, Single<String>> decryptor;

    private OidcSupport(Builder builder) {
        this.oidcConfig = builder.oidcConfig;
        this.enabled = builder.enabled;
        this.cookieHandler = oidcConfig.cookieHandler();

        if (oidcConfig.cookieEncryptionEnabled()) {
            Optional<String> encryptName = oidcConfig.cookieEncryptionName();
            if (encryptName.isPresent()) {
                String encryptionConfigurationName = encryptName.get();
                encryptor = secret -> securityFromContext()
                        .encrypt(encryptionConfigurationName, secret.getBytes(StandardCharsets.UTF_8));
                decryptor = cipherText -> securityFromContext()
                        .decrypt(encryptionConfigurationName, cipherText)
                        .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
            } else {
                char[] masterPassword = oidcConfig.cookieEncryptionPassword()
                        .orElseGet(OidcSupport::generateMasterPassword);

                SymmetricCipher symmetricCipher = SymmetricCipher.create(masterPassword);

                encryptor = secret -> Single.just(symmetricCipher.encrypt(Base64Value.create(secret)).toBase64());
                decryptor = cipherText -> Single.just(symmetricCipher.decrypt(Base64Value.createFromEncoded(cipherText))
                                                              .toDecodedString());
            }
        } else {
            // disabled encryption
            encryptor = Single::just;
            decryptor = Single::just;
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
            rules.get(oidcConfig.redirectUri(), this::processOidcRedirect);
            if (oidcConfig.logoutEnabled()) {
                rules.get(oidcConfig.logoutUri(), this::processLogout);
            }
            rules.any(this::addRequestAsHeader);
        }
    }

    private static Security securityFromContext() {
        return Contexts.context()
                .orElseGet(Contexts::globalContext)
                .get(Security.class)
                .orElseThrow(() -> new SecurityException("When using encryption configuration name for OIDC,"
                                                                 + " Security must be registered with current or"
                                                                 + " global context"));
    }

    private static char[] generateMasterPassword() {
        Path path = Paths.get(".helidon-oidc-secret");
        if (!Files.exists(path)) {

            String password = UUID.randomUUID().toString();
            try {
                Files.writeString(path, password, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (IOException e) {
                throw new SecurityException("Failed to create OIDC secret " + path.toAbsolutePath(), e);
            }
            LOGGER.warning("OIDC requires encryption configuration which was not provided. We will generate a password"
                                   + " that will only work for the current service instance. To disable encryption, use"
                                   + " cookie-encryption-enabled: false configuration, to configure master password, use"
                                   + " cookie-encryption-password: my-master-password (must be configured to same value on all"
                                   + " instances that share the cookie), to configure encryption using security"
                                   + " (support for vaults), use"
                                   + " cookie-encryption-name: name (must have corresponding encryption provider and"
                                   + " configuration with the provided name in security), this also requires Security to be"
                                   + " registered with current or global Context (this works automatically in Helidon MP)."
                                   + " This message is logged just once, before generating the master password");

        }

        try {
            // to be consistent, I always read the content from the file, even when creating it
            return Files.readString(path, StandardCharsets.UTF_8).toCharArray();
        } catch (IOException e) {
            throw new SecurityException("Cannot read OIDC secret file: " + path.toAbsolutePath(), e);
        }
    }

    private void processLogout(ServerRequest req, ServerResponse res) {
        Optional<String> idTokenCookie = req.headers()
                .cookies()
                .first(oidcConfig.cookieName() + "_2");

        idTokenCookie.ifPresent(s -> decryptor.apply(s)
                .forSingle(idToken -> {
                    StringBuilder sb = new StringBuilder(oidcConfig.logoutEndpointUri()
                                                                 + "?id_token_hint="
                                                                 + idToken
                                                                 + "&post_logout_redirect_uri=" + oidcConfig.postLogoutUri());

                    req.queryParams().first("state")
                            .ifPresent(it -> sb.append("&state=").append(it));

                    ResponseHeaders headers = res.headers();
                    headers.addCookie(cookieHandler.removeCookie(oidcConfig.cookieName()).build());
                    headers.addCookie(cookieHandler.removeCookie(oidcConfig.cookieName() + "_2").build());

                    res.status(Http.Status.TEMPORARY_REDIRECT_307)
                            .addHeader(Http.Header.LOCATION, sb.toString())
                            .send();
                }));
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
                .add("redirect_uri", oidcConfig.redirectUriWithHost());

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
            headers.addCookie(cookieHandler.createCookie(oidcConfig.cookieName(), tokenValue).build());

            if (idToken != null && oidcConfig.logoutEnabled()) {
                encryptor.apply(idToken)
                        .forSingle(it -> {
                            headers.addCookie(cookieHandler.createCookie(oidcConfig.cookieName() + "_2", idToken).build());
                            res.send();
                        });
                return "done";
            }
        }

        res.send();

        return "done";
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

    /**
     * A fluent API builder for {@link io.helidon.security.providers.oidc.OidcSupport}.
     */
    public static class Builder implements io.helidon.common.Builder<OidcSupport> {
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
