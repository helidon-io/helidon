package io.helidon.lra;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


public class ThreadContext {
    private static final java.lang.ThreadLocal<ThreadContext> lraContexts = new java.lang.ThreadLocal<>();

    private Stack<URI> stack;

    private ThreadContext(URI url) {
        stack = new Stack<>();
        stack.push(url);
    }

    public static List<Object> getContexts() {
        ThreadContext threadContext = lraContexts.get();

        if (threadContext == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(threadContext.stack);
    }

}

