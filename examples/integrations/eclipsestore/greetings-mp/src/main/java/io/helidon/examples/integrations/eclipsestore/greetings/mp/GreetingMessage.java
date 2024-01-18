/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.eclipsestore.greetings.mp;

/**
 * POJO defining the greeting message content.
 */
@SuppressWarnings("unused")
public class GreetingMessage {
    /**
     * Message.
     */
    private String theMessage;

    /**
     * Create a new GreetingMessage instance.
     */
    public GreetingMessage() {
    }

    /**
     * Create a new GreetingMessage instance.
     *
     * @param message message
     */
    public GreetingMessage(final String message) {
        this.theMessage = message;
    }

    /**
     * Gets the message value.
     *
     * @return message value
     */
    public String getMessage() {
        return theMessage;
    }

    /**
     * Sets the message value.
     *
     * @param message message value to set
     */
    public void setMessage(final String message) {
        this.theMessage = message;
    }
}
