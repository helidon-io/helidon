# Helidon Jedis CDI Integration

The Helidon Jedis CDI Integration project supplies a[CDI portable
extension](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#spi) that lets the
 end user inject various [Jedis](https://github.com/xetorthio/jedis#jedis)
 objects into her CDI-based application.

## Installation

Ensure that the Helidon Jedis CDI Integration project and its runtime
dependencies are present on your application's runtime classpath.

For Maven users, your `<dependency>` stanza should look like this:

```xml
<dependency>
  <groupId>io.helidon.integrations.cdi</groupId>
  <artifactId>helidon-integrations-cdi-jedis</artifactId>
  <version>1.0.0</version>
  <scope>runtime</scope>
</dependency>
```

== Usage

If you want to use a [`JedisPool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPool.html)
named `orders` in your application code, simply inject it in the[usual,
idiomatic CDI way](http://docs.jboss.org/cdi/spec/2.0/cdi-spec.html#injection_and_resolution).
 Here is a field injection example:

```java
@Inject
@Named("orders")
private JedisPool ordersPool;
```

And here is a constructor injection example:

```java
private final JedisPool ordersPool;

@Inject
public YourConstructor(@Named("orders") JedisPool pool) {
  super();
  this.ordersPool = pool;
}
```

The Helidon Jedis CDI Integration project will satisfy this injection
point with a [`JedisPool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPool.html)
that it will create in [application scope](http://docs.jboss.org/cdi/api/2.0/javax/enterprise/context/ApplicationScoped.html).

To create it, the Helidon Jedis CDI Integration project will use [MicroProfile
Config](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/index.html?overview-summary.html)
 to locate its configuration. [Property
names](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/Config.html#getPropertyNames--)
 that start with `redis.clients.jedis.JedisPool.`_instanceName_`.` will be
 parsed, and the remaining portion of each such name will be treated as a Java
 Bean or constructor property of `JedisPool`.

So, for example, a System property with a name like this:

```
redis.clients.jedis.JedisPool.orders.port
```

...set to a value of:

```
6379
```

...together with other similarly-named properties will result
ultimately in a `JedisPool` object injectable under the instance name
of `orders` with a `port` property set to `6379` like so:

```java
@Inject
@Named("orders")
private JedisPool pool;
```

Property names that start with
 `redis.clients.jedis.JedisPool.instanceName.` are parsed, and the
 remaining portion of each name is treated as a Java Bean property of
[`JedisPool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPool.html),
 or as a primitive value accepted by[its
 most specific constructor]( https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPool.html#JedisPool-org.apache.commons.pool2.impl.GenericObjectPoolConfig-java.lang.String-int-int-java.lang.String-int-boolean-javax.net.ssl.SSLSocketFactory-javax.net.ssl.SSLParameters-javax.net.ssl.HostnameVerifier-).
 Because the `JedisPool` class inherits from the
[`Pool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/util/Pool.html)
 class, its writable Java Bean properties are available as well.

Accordingly, the `JedisPool` properties that can be set are as
follows, where `instanceName` should be replaced with the actual name
used in application code:

* `redis.clients.jedis.JedisPool.instanceName.clientName`
* `redis.clients.jedis.JedisPool.instanceName.connectionTimeout`
* `redis.clients.jedis.JedisPool.instanceName.database`
* `redis.clients.jedis.JedisPool.instanceName.host`
* `redis.clients.jedis.JedisPool.instanceName.password`
* `redis.clients.jedis.JedisPool.instanceName.port`
* `redis.clients.jedis.JedisPool.instanceName.socketTimeout`
* `redis.clients.jedis.JedisPool.instanceName.ssl`

Any documentation for these properties that exists may be found in the
javadocs for the [`JedisPool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPool.html)
and [`Pool`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/util/Pool.html) classes.

For advanced configuration use cases, it is also possible to configure
the[`JedisPoolConfig`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPoolConfig.html)
object used by the Helidon Jedis CDI Integration project to create
`JedisPool` instances.  In this case, [property
names](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.3/org/eclipse/microprofile/config/Config.html#getPropertyNames--)
 that start with `redis.clients.jedis.JedisPoolConfig.`_instanceName_`.` will be
parsed, and the remaining portion of each such name will be treated as
a Java Bean or constructor property of `JedisPool` (or of its
superclasses).

Accordingly, the `JedisPoolConfig` Java Bean properties that can be
set are as follows, where `instanceName` should be replaced with the
actual named used in application code:

* `redis.clients.jedis.JedisPoolConfig.instanceName.blockWhenExhausted`
* `redis.clients.jedis.JedisPoolConfig.instanceName.evictionPolicyClassName`
* `redis.clients.jedis.JedisPoolConfig.instanceName.fairness`
* `redis.clients.jedis.JedisPoolConfig.instanceName.jmxEnabled`
* `redis.clients.jedis.JedisPoolConfig.instanceName.jmxNameBase`
* `redis.clients.jedis.JedisPoolConfig.instanceName.jmxNamePrefix`
* `redis.clients.jedis.JedisPoolConfig.instanceName.lifo`
* `redis.clients.jedis.JedisPoolConfig.instanceName.maxIdle`
* `redis.clients.jedis.JedisPoolConfig.instanceName.maxTotal`
* `redis.clients.jedis.JedisPoolConfig.instanceName.maxWaitMillis`
* `redis.clients.jedis.JedisPoolConfig.instanceName.minEvictableTimeMillis`
* `redis.clients.jedis.JedisPoolConfig.instanceName.minIdle`
* `redis.clients.jedis.JedisPoolConfig.instanceName.numTestsPerEvictionRun`
* `redis.clients.jedis.JedisPoolConfig.instanceName.softMinEvictableIdleTimeMillis`
* `redis.clients.jedis.JedisPoolConfig.instanceName.testOnBorrow`
* `redis.clients.jedis.JedisPoolConfig.instanceName.testOnCreate`
* `redis.clients.jedis.JedisPoolConfig.instanceName.testOnReturn`
* `redis.clients.jedis.JedisPoolConfig.instanceName.testWhileIdle`
* `redis.clients.jedis.JedisPoolConfig.instanceName.timeBetweenEvictionRunsMillis`

Any documentation for these properties that exists may be found in the
javadocs for the [`JedisPoolConfig`](https://static.javadoc.io/redis.clients/jedis/2.9.0/redis/clients/jedis/JedisPoolConfig.html),
[`GenericObjectPoolConfig`](https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPoolConfig.html)
and [`BaseObjectPoolConfig`](https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/BaseObjectPoolConfig.html)
classes.