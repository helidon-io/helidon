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

package io.helidon.security.examples.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutionException;

import io.helidon.security.AuthenticationResponse;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;

/**
 * This class shows how to manually secure your application.
 */
public class ProgrammaticSecurity {
    private static final ThreadLocal<SecurityContext> CONTEXT = new ThreadLocal<>();
    private Security security;

    /**
     * Entry point to this example.
     *
     * @param args no needed
     * @throws ExecutionException   if asynchronous security fails
     * @throws InterruptedException if asynchronous security gets interrupted
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ProgrammaticSecurity instance = new ProgrammaticSecurity();

        /*
         * Simple single threaded applications - nothing too complicated
         */
        //1: initialize security component
        instance.init();
        //2: login
        Subject subject = instance.login();

        //3: authorize access to restricted resource
        instance.execute();
        //4: propagate identity
        instance.propagate();

        /*
         * More complex - multithreaded application
         */
        instance.multithreaded(subject);

    }

    private static String buildBasic(String user, String password) {
        return "basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private void multithreaded(Subject subject) {
        Thread thread = new Thread(() -> {
            try {
                SecurityContext context = security.contextBuilder("newThread")
                        .build();

                CONTEXT.set(context);

                //this must be done, as there is no subject (yet) for current thread (or event the login attempt may be done
                //in this thread - depends on what your application wants to do...
                context.runAs(subject, () -> {
                    //3: authorize access to restricted resource
                    execute();
                    //4: propagate identity
                    propagate();
                });
            } finally {
                CONTEXT.remove();
            }
        });
        thread.start();
    }

    private void propagate() {
        OutboundSecurityResponse response = CONTEXT.get().outboundClientBuilder().buildAndGet();

        switch (response.status()) {
        case SUCCESS:
            //we should have "Authorization" header present and just need to update request headers of our outbound call
            System.out.println("Authorization header: " + response.requestHeaders().get("Authorization"));
            break;
        case SUCCESS_FINISH:
            System.out.println("Identity propagation done, request sent...");
            break;
        default:
            System.out.println("Failed in identity propagation provider: " + response.description().orElse(null));
            break;
        }
    }

    private void execute() {
        SecurityContext context = CONTEXT.get();
        //check role
        if (!context.isUserInRole("theRole")) {
            throw new IllegalStateException("User is not in expected role");
        }

        context.env(context.env()
                               .derive()
                               .addAttribute("resourceType", "CustomResourceType"));

        //check authorization through provider
        AuthorizationResponse response = context.atzClientBuilder().buildAndGet();

        if (response.status().isSuccess()) {
            //ok, process resource
            System.out.println("Resource processed");
        } else {
            System.out.println("You are not permitted to process resource");
        }
    }

    private Subject login() {
        SecurityContext securityContext = CONTEXT.get();
        securityContext.env(securityContext.env().derive()
                                       .path("/some/path")
                                       .header("Authorization", buildBasic("aUser", "aPassword")));

        AuthenticationResponse response = securityContext.atnClientBuilder().buildAndGet();

        if (response.status().isSuccess()) {
            return response.user().orElseThrow(() -> new IllegalStateException("No user authenticated!"));
        }

        throw new RuntimeException("Failed to authenticate", response.throwable().orElse(null));
    }

    private void init() {
        //binds security context to current thread
        this.security = Security.builder()
                .addProvider(new MyProvider(), "FirstProvider")
                .build();
        CONTEXT.set(security.contextBuilder("mainThread").build());
    }

}
