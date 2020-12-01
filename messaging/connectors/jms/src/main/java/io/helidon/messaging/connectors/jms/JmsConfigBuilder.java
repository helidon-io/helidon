/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.messaging.ConnectorConfigBuilder;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;


/**
 * Build Jms specific config.
 */
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
     * To select from manually configured {@link javax.jms.ConnectionFactory ConnectionFactories} over
     * {@link JmsConnector.JmsConnectorBuilder#connectionFactory(String, javax.jms.ConnectionFactory)
     * JmsConnectorBuilder#connectionFactory()}.
     *
     * @param factoryName connection factory name
     * @return this builder
     */
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
     * <ul>
     * <li>Type: enum</li>
     * <li>Default: AUTO_ACKNOWLEDGE</li>
     * <li>Valid Values: AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE</li>
     * </ul>
     *
     * @param acknowledgeMode AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     * @return this builder
     */
    public JmsConfigBuilder acknowledgeMode(AcknowledgeMode acknowledgeMode) {
        super.property("acknowledge-mode", acknowledgeMode.name());
        return this;
    }

    /**
     * Indicates whether the session will use a local transaction.
     *
     * <ul>
     * <li>Type: boolean</li>
     * <li>Default: false</li>
     * <li>Valid Values: true, false</li>
     * </ul>
     *
     * @param transacted true if so
     * @return this builder
     */
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
    public JmsConfigBuilder password(String password) {
        super.property("password", password);
        return this;
    }

    /**
     * Specify if connection is {@link Type#QUEUE queue}  or {@link Type#TOPIC topic}.
     *
     * <ul>
     * <li>Type: enum</li>
     * <li>Default: {@link Type#QUEUE QUEUE}</li>
     * <li>Valid Values: {@link Type#QUEUE QUEUE}, {@link Type#TOPIC TOPIC}</li>
     * </ul>
     *
     * @param type {@link Type#QUEUE queue} or {@link Type#TOPIC topic}
     * @return this builder
     */
    public JmsConfigBuilder type(Type type) {
        super.property("type", type.toString());
        return this;
    }

    /**
     * Queue or topic name.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param destination queue or topic name
     * @return this builder
     */
    public JmsConfigBuilder destination(String destination) {
        super.property("destination", destination);
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
    public JmsConfigBuilder messageSelector(String messageSelector) {
        super.property("message-selector", messageSelector);
        return this;
    }

    /**
     * Timeout for polling for next message in every poll cycle in millis.
     *
     * <ul>
     * <li>Type: milliseconds</li>
     * <li>Default: 50</li>
     * </ul>
     *
     * @param pollTimeout timeout of polling for next message
     * @return this builder
     */
    public JmsConfigBuilder pollTimeout(long pollTimeout) {
        super.property("poll-timeout", String.valueOf(pollTimeout));
        return this;
    }

    /**
     * Period for executing poll cycles in millis.
     *
     * <ul>
     * <li>Type: milliseconds</li>
     * <li>Default: 100</li>
     * </ul>
     *
     * @param periodExecutions period for executing poll cycles in millis
     * @return this builder
     */
    public JmsConfigBuilder periodExecutions(long periodExecutions) {
        super.property("period-executions", String.valueOf(periodExecutions));
        return this;
    }

    /**
     * When multiple channels share same session-group-id,
     * they share same JMS session.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param sessionGroupId identifier for channels sharing same JMS session
     * @return this builder
     */
    public JmsConfigBuilder sessionGroupId(String sessionGroupId) {
        super.property("session-group-id", sessionGroupId);
        return this;
    }

    /**
     * JNDI name of JMS factory.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param jndiJmsFactory JNDI name of JMS factory
     * @return this builder
     */
    public JmsConfigBuilder jndiJmsFactory(String jndiJmsFactory) {
        super.property("jndi.jms-factory", jndiJmsFactory);
        return this;
    }

    /**
     * JNDI initial factory.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param jndiInitialFactory JNDI initial factory
     * @return this builder
     */
    public JmsConfigBuilder jndiInitialFactory(String jndiInitialFactory) {
        super.property("jndi." + JmsConnector.JNDI_PROPS_ATTRIBUTE + ".java.naming.factory.initial", jndiInitialFactory);
        return this;
    }

    /**
     * JNDI initial factory.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param jndiInitialFactory JNDI initial factory
     * @return this builder
     */
    public JmsConfigBuilder jndiInitialFactory(Class<? extends InitialContextFactory> jndiInitialFactory) {
        this.jndiInitialFactory(jndiInitialFactory.getName());
        return this;
    }

    /**
     * JNDI provider url.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param jndiProviderUrl JNDI provider url
     * @return this builder
     */
    public JmsConfigBuilder jndiProviderUrl(String jndiProviderUrl) {
        super.property("jndi." + JmsConnector.JNDI_PROPS_ATTRIBUTE + ".java.naming.provider.url", jndiProviderUrl);
        return this;
    }

    /**
     * Environment properties used for creating initial context java.naming.factory.initial, java.naming.provider.url.
     *
     * <ul>
     * <li>Type: string</li>
     * </ul>
     *
     * @param initialContextProps properties used for creating JNDI initial context
     * @return this builder
     */
    public JmsConfigBuilder jndiInitialContextProperties(Map<String, String> initialContextProps) {
        initialContextProps.forEach((key, val) -> super.property("jndi.env-properties." + key, val));
        return this;
    }


    /**
     * Type of the JMS connection.
     */
    public enum Type {
        /**
         * Queue connection type, every message is consumed by one client only.
         */
        QUEUE,
        /**
         * Topic connection type, every message is delivered to all subscribed clients.
         */
        TOPIC;

        @Override
        public String toString() {
            return super.name().toLowerCase();
        }
    }
}
