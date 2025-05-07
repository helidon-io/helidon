/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.jndi;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

class ChildContext implements Context {
    private final RootContext rootContext;
    private final Name name;
    private final Hashtable<Object, Object> environment;

    ChildContext(RootContext rootContext, Name name) {
        this.rootContext = rootContext;
        this.name = name;
        this.environment = new Hashtable<>(rootContext.getEnvironment());
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return rootContext.lookup(resolveName(name));
    }

    @Override
    public Object lookup(String name) throws NamingException {
        return rootContext.lookup(resolveName(name));
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        rootContext.bind(resolveName(name), obj);
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        rootContext.bind(resolveName(name), obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        rootContext.rebind(resolveName(name), obj);
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        rootContext.rebind(resolveName(name), obj);
    }

    @Override
    public void unbind(Name name) throws NamingException {
        rootContext.unbind(resolveName(name));
    }

    @Override
    public void unbind(String name) throws NamingException {
        rootContext.unbind(resolveName(name));
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        rootContext.rename(resolveName(oldName), resolveName(newName));
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        rootContext.rename(resolveName(oldName), resolveName(newName));
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        return rootContext.list(resolveName(name));
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        return rootContext.list(resolveName(name));
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return rootContext.listBindings(resolveName(name));
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        return rootContext.listBindings(resolveName(name));
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        rootContext.destroySubcontext(resolveName(name));
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
        rootContext.destroySubcontext(resolveName(name));
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        return rootContext.createSubcontext(resolveName(name));
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return rootContext.createSubcontext(resolveName(name));
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return rootContext.lookupLink(resolveName(name));
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return rootContext.lookupLink(resolveName(name));
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        return rootContext.getNameParser(resolveName(name));
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return rootContext.getNameParser(resolveName(name));
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return rootContext.composeName(resolveName(name), prefix);
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        return rootContext.composeName(resolveName(name), getNameParser(name).parse(prefix)).toString();
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return environment.put(propName, propVal);
    }

    @Override
    public Object removeFromEnvironment(String propName) {
        return environment.remove(propName);
    }

    @Override
    public Hashtable<?, ?> getEnvironment() {
        return new Hashtable<>(environment);
    }

    @Override
    public void close() {
    }

    @Override
    public String getNameInNamespace() {
        return this.name.toString();
    }

    private Name resolveName(Name name) throws NamingException {
        return resolveName(name.toString());
    }

    private Name resolveName(String name) throws NamingException {
        return rootContext.nameParser().parse(this.name + "/" + name);
    }
}
