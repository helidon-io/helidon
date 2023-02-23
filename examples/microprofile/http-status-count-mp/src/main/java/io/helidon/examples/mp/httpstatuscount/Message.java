/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.mp.httpstatuscount;

/**
 * Message sent as greetings to client.
 */
public class Message {

    private String message;

    private String greeting;

    /**
     * Creates a new message instance.
     */
    public Message() {
    }

    /**
     * Creates a new message instance with an initial greeting.
     * @param message initial greeting message
     */
    public Message(String message) {
        this.message = message;
    }

    /**
     * Sets the greeting message.
     *
     * @param message message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     *
     * @return the greeting
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Sets the greeting.
     *
     * @param greeting new greeting
     */
    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    /**
     *
     * @return the greeting
     */
    public String getGreeting() {
        return this.greeting;
    }
}
