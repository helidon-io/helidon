/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import java.util.Map;

import javax.naming.spi.InitialContextFactory;

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.messaging.ConnectorConfigBuilder;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;


/**
 * Build Jms specific config.
 */
@Configured
public final class JmsConfigBuilder extends ConnectorConfigBuilder {

    JmsConfigBuilder() {
        super();
        super.property(ConnectorFactory.CONNECTOR_ATTRIBUTE, JmsConnector.CONNECTOR_NAME);
    }

    /**
     * Add custom property.
     *
     * @param key   property key
     * @param value property value
     * @return this builder
     */
    public JmsConfigBuilder property(String key, String value) {
        super.property(key, value);
        return this;
    }

    /**
     * To select from manually configured {@link jakarta.jms.ConnectionFactory ConnectionFactories} over
     * {@link JmsConnector.JmsConnectorBuilder#connectionFactory(String, jakarta.jms.ConnectionFactory)
     * JmsConnectorBuilder#connectionFactory()}.
     *
     * @param factoryName connection factory name
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder namedFactory(String factoryName) {
        super.property(JmsConnector.NAMED_FACTORY_ATTRIBUTE, factoryName);
        return this;
    }

    /**
     * JMS acknowledgement mode.
     * <ul>
     * <li><b>AUTO_ACKNOWLEDGE</b> Acknowledges automatically after message reception over JMS api.</li>
     * <li><b>CLIENT_ACKNOWLEDGE</b> Message is acknowledged when
     * {@link org.eclipse.microprofile.reactive.messaging.Message#ack Message.ack()} is invoked either manually or
     * by {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment Acknowledgment} policy.</li>
     * <li><b>DUPS_OK_ACKNOWLEDGE</b> Messages are acknowledged lazily which can result in
     * duplicate messages being delivered.</li>
     * </ul>
     *
     * @param acknowledgeMode AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     * @return this builder
     */
    @ConfiguredOption("AUTO_ACKNOWLEDGE")
    public JmsConfigBuilder acknowledgeMode(AcknowledgeMode acknowledgeMode) {
        super.property("acknowledge-mode", acknowledgeMode.name());
        return this;
    }

    /**
     * Indicates whether the session will use a local transaction.
     *
     * @param transacted true if so
     * @return this builder
     */
    @ConfiguredOption("false")
    public JmsConfigBuilder transacted(boolean transacted) {
        super.property("transacted", String.valueOf(transacted));
        return this;
    }

    /**
     * User name used for creating JMS connection.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param username JMS connection user name
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder username(String username) {
        super.property("username", username);
        return this;
    }

    /**
     * Password used for creating JMS connection.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param password JMS connection password
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder password(String password) {
        super.property("password", password);
        return this;
    }

    /**
     * Specify if connection is {@link Type#QUEUE queue}  or {@link Type#TOPIC topic}.
     *
     * @param type {@link Type#QUEUE queue} or {@link Type#TOPIC topic}
     * @return this builder
     */
    @ConfiguredOption("QUEUE")
    public JmsConfigBuilder type(Type type) {
        super.property("type", type.toString());
        return this;
    }

    /**
     * Queue or topic name.
     *
     * @param destination queue or topic name
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder destination(String destination) {
        super.property("destination", destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#QUEUE QUEUE} as type.
     *
     * @param destination queue name
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder queue(String destination) {
        this.type(Type.QUEUE);
        this.destination(destination);
        return this;
    }

    /**
     * Use supplied destination name and {@link Type#TOPIC TOPIC} as type.
     *
     * @param destination topic name
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder topic(String destination) {
        this.type(Type.TOPIC);
        this.destination(destination);
        return this;
    }

    /**
     * JMS API message selector expression based on a subset of the SQL92.
     * Expression can only access headers and properties, not the payload.
     *
     * <ul>
     * <li>Type: string</li>
     * <li>Example: NewsType = ’Sports’ OR NewsType = ’Opinion’</li>
     * </ul>
     *
     * @param messageSelector message selector expression
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder messageSelector(String messageSelector) {
        super.property("message-selector", messageSelector);
        return this;
    }

    /**
     * Timeout for polling for next message in every poll cycle in millis.
     *
     * @param pollTimeout timeout of polling for next message
     * @return this builder
     */
    @ConfiguredOption("50")
    public JmsConfigBuilder pollTimeout(long pollTimeout) {
        super.property("poll-timeout", String.valueOf(pollTimeout));
        return this;
    }

    /**
     * Period for executing poll cycles in millis.
     *
     * @param periodExecutions period for executing poll cycles in millis
     * @return this builder
     */
    @ConfiguredOption("100")
    public JmsConfigBuilder periodExecutions(long periodExecutions) {
        super.property("period-executions", String.valueOf(periodExecutions));
        return this;
    }

    /**
     * When multiple channels share same session-group-id,
     * they share same JMS session.
     *
     * @param sessionGroupId identifier for channels sharing same JMS session
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder sessionGroupId(String sessionGroupId) {
        super.property("session-group-id", sessionGroupId);
        return this;
    }

    /**
     * JNDI name of JMS factory.
     *
     * @param jndiJmsFactory JNDI name of JMS factory
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder jndiJmsFactory(String jndiJmsFactory) {
        super.property("jndi.jms-factory", jndiJmsFactory);
        return this;
    }

    /**
     * JNDI initial factory.
     *
     * @param jndiInitialFactory JNDI initial factory
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder jndiInitialFactory(String jndiInitialFactory) {
        super.property("jndi." + JmsConnector.JNDI_PROPS_ATTRIBUTE + ".java.naming.factory.initial", jndiInitialFactory);
        return this;
    }

    /**
     * JNDI initial factory.
     *
     * @param jndiInitialFactory JNDI initial factory
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder jndiInitialFactory(Class<? extends InitialContextFactory> jndiInitialFactory) {
        this.jndiInitialFactory(jndiInitialFactory.getName());
        return this;
    }

    /**
     * JNDI provider url.
     *
     * @param jndiProviderUrl JNDI provider url
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder jndiProviderUrl(String jndiProviderUrl) {
        super.property("jndi." + JmsConnector.JNDI_PROPS_ATTRIBUTE + ".java.naming.provider.url", jndiProviderUrl);
        return this;
    }

    /**
     * Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url.
     *
     * @param initialContextProps properties used for creating JNDI initial context
     * @return this builder
     */
    @ConfiguredOption
    public JmsConfigBuilder jndiInitialContextProperties(Map<String, String> initialContextProps) {
        initialContextProps.forEach((key, val) -> super.property("jndi.env-properties." + key, val));
        return this;
    }


}
