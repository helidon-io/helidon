/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.webserver.spi.UpgradeCodecProvider;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;

class UpgradeManager {
    private static final Map<CharSequence, UpgradeCodecProvider> UPGRADE_HANDLERS = new HashMap<>();
    private static final ApplicationProtocolConfig APPLICATION_PROTOCOL_CONFIG;

    private static final boolean NO_UPGRADES;

    static {
        List<String> alpnProtocols = new ArrayList<>();

        HelidonServiceLoader.create(ServiceLoader.load(UpgradeCodecProvider.class))
                .forEach(upgradeHandlerSupplier -> {
                    UPGRADE_HANDLERS.put(upgradeHandlerSupplier.clearTextProtocol(), upgradeHandlerSupplier);
                    upgradeHandlerSupplier.tlsProtocol().ifPresent(alpnProtocols::add);
                });

        NO_UPGRADES = UPGRADE_HANDLERS.isEmpty();

        // Default
        alpnProtocols.add(ApplicationProtocolNames.HTTP_1_1);

        APPLICATION_PROTOCOL_CONFIG = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                alpnProtocols
        );
    }

    private UpgradeManager() {
    }

    static ApplicationProtocolConfig alpnConfig() {
        return APPLICATION_PROTOCOL_CONFIG;
    }

    // Used in case HTTP/2 for prior knowledge
    static Optional<ChannelHandler> priorKnowledgeWrapper(HttpServerCodec httpServerCodec,
                                                          HttpServerUpgradeHandler wrappedUpgradeHandler,
                                                          int maxContentLength) {
        return UPGRADE_HANDLERS.values().stream()
                .map(uhs -> uhs.priorKnowledgeDecoder(httpServerCodec, wrappedUpgradeHandler, maxContentLength))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }

    static void addUpgradeHandler(ChannelPipeline p,
                                  Router router,
                                  HttpServerCodec sourceCodec,
                                  int maxContentLength) {
        if (NO_UPGRADES) {
            p.addLast(sourceCodec);
            return;
        }

        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec,
                protocol -> {
                    // Select proper decoder by protocol name
                    UpgradeCodecProvider upgradeCodecProvider = UPGRADE_HANDLERS.get(protocol);
                    if (upgradeCodecProvider == null) {
                        return null;
                    }
                    return upgradeCodecProvider.upgradeCodec(sourceCodec, router, maxContentLength);
                }, maxContentLength);

        // Prior-knowledge decoder needs to wrap upgrade handler
        Optional<ChannelHandler> priorKnowledgeWrapper = priorKnowledgeWrapper(sourceCodec, upgradeHandler, maxContentLength);

        if (priorKnowledgeWrapper.isEmpty()) {
            // PriorKnowledgeWrapper adds this codec on its own
            // needs to be added before upgrade handler
            p.addLast(sourceCodec);
            // No prior knowledge decoder, add update handler directly
            p.addLast(upgradeHandler);
        } else {
            // Wrap upgrade handler inside prior knowledge decoder
            p.addLast(priorKnowledgeWrapper.get());
        }
    }
}
