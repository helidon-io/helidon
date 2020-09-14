/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpsign;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;

/**
 * A provider that can authenticate incoming requests based on HTTP signature of header fields, and
 * can create signatures for outbound requests.
 */
public final class HttpSignProvider implements AuthenticationProvider, OutboundSecurityProvider {
    static final String ALGORITHM_HMAC = "hmac-sha256";
    static final String ALGORITHM_RSA = "rsa-sha256";
    static final SignedHeadersConfig DEFAULT_REQUIRED_HEADERS = SignedHeadersConfig.builder()
            .defaultConfig(SignedHeadersConfig.HeadersConfig
                                   .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET)))
            .config("get", SignedHeadersConfig.HeadersConfig
                    .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host"),
                            List.of("authorization")))
            .config("head", SignedHeadersConfig.HeadersConfig
                    .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host"),
                            List.of("authorization")))
            .config("delete", SignedHeadersConfig.HeadersConfig
                    .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host"),
                            List.of("authorization")))
            .config("put", SignedHeadersConfig.HeadersConfig
                    .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host"),
                            List.of("authorization")))
            .config("post", SignedHeadersConfig.HeadersConfig
                    .create(List.of("date", SignedHeadersConfig.REQUEST_TARGET, "host"),
                            List.of("authorization")))
            .build();
    static final String ATTRIB_NAME_KEY_ID = HttpSignProvider.class.getName() + ".keyId";

    private final boolean optional;
    private final String realm;
    private final Set<HttpSignHeader> acceptHeaders;
    private final SignedHeadersConfig inboundRequiredHeaders;
    private final Map<String, InboundClientDefinition> inboundKeys;
    private final OutboundConfig outboundConfig;
    // cache of target name to a signature configuration for outbound calls
    private final Map<String, OutboundTargetDefinition> targetKeys = new HashMap<>();

    private HttpSignProvider(Builder builder) {
        this.optional = builder.optional;
        this.realm = builder.realm;
        this.acceptHeaders = (
                builder.acceptHeaders.isEmpty()
                        ? EnumSet.of(HttpSignHeader.SIGNATURE, HttpSignHeader.AUTHORIZATION)
                        : EnumSet.copyOf(builder.acceptHeaders));
        this.inboundRequiredHeaders = builder.inboundRequiredHeaders;
        this.inboundKeys = builder.inboundKeys;
        this.outboundConfig = builder.outboundConfig;

        outboundConfig.targets().forEach(target -> target.getConfig().ifPresent(targetConfig -> {
            OutboundTargetDefinition outboundTargetDefinition = targetConfig.get("signature")
                    .as(OutboundTargetDefinition::create)
                    .get();
            targetKeys.put(target.name(), outboundTargetDefinition);
        }));
    }

    /**
     * Create a new instance of this provider from configuration.
     *
     * @param config config located at this provider, expects "http-signature" to be a child
     * @return provider configured from config
     */
    public static HttpSignProvider create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a builder to build this provider.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletionStage<AuthenticationResponse> authenticate(ProviderRequest providerRequest) {
        Map<String, List<String>> headers = providerRequest.env().headers();

        if ((headers.get("Signature") != null) && acceptHeaders.contains(HttpSignHeader.SIGNATURE)) {
            return CompletableFuture
                    .supplyAsync(() -> signatureHeader(headers.get("Signature"), providerRequest.env()),
                                 providerRequest.securityContext().executorService());
        } else if ((headers.get("Authorization") != null) && acceptHeaders.contains(HttpSignHeader.AUTHORIZATION)) {
            // TODO when authorization header in use and "authorization" is also a
            // required header to be signed, we must either fail or ignore, as we cannot sign ourselves
            return CompletableFuture
                    .supplyAsync(() -> authorizeHeader(providerRequest.env()),
                                 providerRequest.securityContext().executorService());
        }

        if (optional) {
            return CompletableFuture.completedFuture(AuthenticationResponse.abstain());
        }
        return CompletableFuture
                .completedFuture(AuthenticationResponse.failed("Missing header. Accepted headers: " + acceptHeaders));
    }

    private AuthenticationResponse authorizeHeader(SecurityEnvironment env) {
        List<String> authorization = env.headers().get("Authorization");
        AuthenticationResponse response = null;

        // attempt to validate each authorization, first one that succeeds will finish processing and return
        for (String authorizationValue : authorization) {
            if (authorizationValue.toLowerCase().startsWith("signature ")) {
                response = signatureHeader(List.of(authorizationValue.substring("singature ".length())), env);
                if (response.status().isSuccess()) {
                    // that was a good header, let's return the response
                    return response;
                }
            }
        }

        // we have reached the end - all headers validated, none fit, fail or abstain
        if (optional) {
            return AuthenticationResponse.abstain();
        }

        // challenge
        return challenge(env, (null == response)
                ? "No Signature authorization header"
                : response.description().orElse("Unknown problem"));
    }

    private AuthenticationResponse challenge(SecurityEnvironment env, String description) {
        return AuthenticationResponse.builder()
                .responseHeader("WWW-Authenticate", "Signature realm=\""
                        + realm
                        + ",headers=\""
                        + headersForMethod(env.method())
                        + "\"")
                .status(SecurityResponse.SecurityStatus.FAILURE)
                .statusCode(401)
                .description(description)
                .build();
    }

    private String headersForMethod(String method) {
        return String.join(" ", inboundRequiredHeaders.headers(method.toLowerCase()));
    }

    private AuthenticationResponse signatureHeader(List<String> signatures,
                                                   SecurityEnvironment env) {

        /*
            Signature keyId="rsa-key-1",algorithm="rsa-sha256",
            headers="(request-target) host date digest content-length",
            signature="Base64(RSA-SHA256(signing string))"
         */
        String lastError = signatures.isEmpty() ? "No signature values for Signature header" : null;

        for (String signature : signatures) {
            HttpSignature httpSignature = HttpSignature.fromHeader(signature);
            Optional<String> validate = httpSignature.validate();
            if (validate.isPresent()) {
                lastError = validate.get();
            } else {
                //this is a valid signature object, let's validate against key
                InboundClientDefinition clientDefinition = inboundKeys.get(httpSignature.getKeyId());
                if (null == clientDefinition) {
                    lastError = "Client definition for client with key " + httpSignature.getKeyId() + " not found";
                    continue;
                }
                //now we have a signature with a valid keyId - if validation fails, we fail
                return validateSignature(env, httpSignature, clientDefinition);
            }
        }

        if (optional) {
            return AuthenticationResponse.abstain();
        } else {
            return AuthenticationResponse.failed(lastError);
        }
    }

    private AuthenticationResponse validateSignature(SecurityEnvironment env,
                                                     HttpSignature httpSignature,
                                                     InboundClientDefinition clientDefinition) {
        // validate algorithm
        Optional<String> validationResult = httpSignature.validate(env,
                                                                   clientDefinition,
                                                                   inboundRequiredHeaders.headers(env.method(),
                                                                                                  env.headers()));

        if (validationResult.isPresent()) {
            return AuthenticationResponse.failed(validationResult.get());
        }

        Principal principal = Principal.builder()
                .name(clientDefinition.principalName())
                .addAttribute(ATTRIB_NAME_KEY_ID, clientDefinition.keyId())
                .build();

        Subject subject = Subject.builder()
                .principal(principal)
                .build();
        if (clientDefinition.subjectType() == SubjectType.USER) {
            return AuthenticationResponse.success(subject);
        } else {
            return AuthenticationResponse.successService(subject);
        }
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outboundEnv,
                                       EndpointConfig outboundConfig) {
        return this.outboundConfig.findTarget(outboundEnv).isPresent();
    }

    @Override
    public CompletionStage<OutboundSecurityResponse> outboundSecurity(ProviderRequest providerRequest,
                                                                      SecurityEnvironment outboundEnv,
                                                                      EndpointConfig outboundConfig) {

        return CompletableFuture.supplyAsync(() -> signRequest(outboundEnv),
                                             providerRequest.securityContext().executorService());
    }

    private OutboundSecurityResponse signRequest(SecurityEnvironment outboundEnv) {

        Optional<OutboundTarget> targetOpt = this.outboundConfig.findTarget(outboundEnv);

        return targetOpt.map(target -> {
            OutboundTargetDefinition targetConfig = this.targetKeys.computeIfAbsent(target.name(), key -> target.getConfig()
                    .flatMap(config -> config.get("signature").as(OutboundTargetDefinition.class).asOptional())
                    .orElse(target.customObject(OutboundTargetDefinition.class).orElseThrow(() -> new HttpSignatureException(
                            "Failed to find configuration for outbound signatures for target " + target.name()))));

            Map<String, List<String>> newHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            newHeaders.putAll(outboundEnv.headers());
            HttpSignature signature = HttpSignature.sign(outboundEnv, targetConfig, newHeaders);

            OutboundSecurityResponse.Builder builder = OutboundSecurityResponse.builder()
                    .requestHeaders(newHeaders)
                    .status(SecurityResponse.SecurityStatus.SUCCESS);

            switch (targetConfig.header()) {
            case SIGNATURE:
                builder.requestHeader("Signature", signature.toSignatureHeader());
                break;
            case AUTHORIZATION:
                builder.requestHeader("Authorization", "Signature " + signature.toSignatureHeader());
                break;
            default:
                throw new HttpSignatureException("Invalid header configuration: " + targetConfig.header());
            }

            Map<String, List<String>> headers = outboundEnv.headers();
            if (headers.containsKey("host")) {
                builder.requestHeader("host", headers.get("host"));
            }

            if (headers.containsKey("date")) {
                builder.requestHeader("date", headers.get("date"));
            }

            return builder.build();
        }).orElse(OutboundSecurityResponse.empty());
    }

    /**
     * Fluent API builder for this provider. Call {@link #build()} to create a provider instance.
     */
    public static final class Builder implements io.helidon.common.Builder<HttpSignProvider> {
        private boolean optional = true;
        private String realm = "helidon";
        private final Set<HttpSignHeader> acceptHeaders = EnumSet.noneOf(HttpSignHeader.class);
        private SignedHeadersConfig inboundRequiredHeaders = SignedHeadersConfig.builder().build();
        private OutboundConfig outboundConfig = OutboundConfig.builder().build();
        private final Map<String, InboundClientDefinition> inboundKeys = new HashMap<>();

        private Builder() {
        }

        @Override
        public HttpSignProvider build() {
            return new HttpSignProvider(this);
        }

        /**
         * Create a builder from configuration.
         *
         * @param config Config located at http-signatures key
         * @return builder instance configured from config
         */
        public Builder config(Config config) {
            config.get("headers").asList(HttpSignHeader.class).ifPresent(list -> list.forEach(this::addAcceptHeader));
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("realm").asString().ifPresent(this::realm);
            config.get("sign-headers").as(SignedHeadersConfig::create).ifPresent(this::inboundRequiredHeaders);
            outboundConfig = OutboundConfig.create(config);

            config.get("inbound.keys")
                    .asList(InboundClientDefinition::create)
                    .ifPresent(list -> list.forEach(inbound -> inboundKeys.put(inbound.keyId(), inbound)));

            return this;
        }

        /**
         * Add outbound targets to this builder.
         * The targets are used to chose what to do for outbound communication.
         * The targets should have {@link OutboundTargetDefinition} attached through
         * {@link OutboundTarget.Builder#customObject(Class, Object)} to tell us how to sign
         * the request.
         * <p>
         * The same can be done through configuration:
         * <pre>
         * {
         *  name = "http-signatures"
         *  class = "HttpSignProvider"
         *  http-signatures {
         *      targets: [
         *      {
         *          name = "service2"
         *          hosts = ["localhost"]
         *          paths = ["/service2/.*"]
         *
         *          # This configures the {@link OutboundTargetDefinition}
         *          signature {
         *              key-id = "service1"
         *              hmac.secret = "${CLEAR=password}"
         *          }
         *      }]
         *  }
         * }
         * </pre>
         *
         * @param targets targets to select correct outbound security
         * @return updated builder instance
         */
        public Builder outbound(OutboundConfig targets) {
            this.outboundConfig = targets;
            return this;
        }

        /**
         * Add inbound configuration. This is used to validate signature and authenticate the
         * party.
         * <p>
         * The same can be done through configuration:
         * <pre>
         * {
         *  name = "http-signatures"
         *  class = "HttpSignProvider"
         *  http-signatures {
         *      inbound {
         *          # This configures the {@link InboundClientDefinition}
         *          keys: [
         *          {
         *              key-id = "service1"
         *              hmac.secret = "${CLEAR=password}"
         *          }]
         *      }
         *  }
         * }
         * </pre>
         *
         * @param client a single client configuration for inbound communication
         * @return updated builder instance
         */
        public Builder addInbound(InboundClientDefinition client) {
            this.inboundKeys.put(client.keyId(), client);
            return this;
        }

        /**
         * Override the default inbound required headers (e.g. headers that MUST be signed and
         * headers that MUST be signed IF present).
         * <p>
         * Defaults:
         * <ul>
         * <li>get, head, delete methods: date, (request-target), host are mandatory; authorization if present (unless we are
         * creating/validating the {@link HttpSignHeader#AUTHORIZATION} ourselves</li>
         * <li>put, post: same as above, with addition of: content-length, content-type and digest if present
         * <li>for other methods: date, (request-target)</li>
         * </ul>
         * Note that this provider DOES NOT validate the "Digest" HTTP header, only the signature.
         *
         * @param inboundRequiredHeaders headers configuration
         * @return updated builder instance
         */
        public Builder inboundRequiredHeaders(SignedHeadersConfig inboundRequiredHeaders) {
            this.inboundRequiredHeaders = inboundRequiredHeaders;
            return this;
        }

        /**
         * Add a header that is validated on inbound requests. Provider may support more than
         * one header to validate.
         *
         * @param header header to look for signature
         * @return updated builder instance
         */
        public Builder addAcceptHeader(HttpSignHeader header) {
            this.acceptHeaders.add(header);
            return this;
        }

        /**
         * Set whether the signature is optional. If set to true (default), this provider will
         * {@link SecurityResponse.SecurityStatus#ABSTAIN} from this request if signature is not
         * present. If set to false, this provider will {@link SecurityResponse.SecurityStatus#FAILURE fail}
         * if signature is not present.
         *
         * @param optional true for optional singatures
         * @return updated builder instance
         */
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Realm to use for challenging inbound requests that do not have "Authorization" header
         * in case header is {@link HttpSignHeader#AUTHORIZATION} and singatures are not optional.
         *
         * @param realm realm to challenge with, defautls to "helidon"
         * @return updated builder instance
         */
        public Builder realm(String realm) {
            this.realm = realm;
            return this;
        }
    }
}
