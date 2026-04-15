# Introducing Helidon

Helidon is a framework for developing Java applications using a microservices architecture. It simplifies and streamlines the development, deployment, and management of cloud-native applications.

Helidon applications are standalone Java applications running in their own JVM and powered by the Helidon WebServer. Helidon is compatible with Java SE and many Jakarta EE specifications. It includes a full observability stack with dedicated components for health checks, metrics, tracing, and logging. It also integrates well with several other popular tools and technologies so you can feel confident that you won’t lose any functionality moving to the Helidon framework.

Helidon is available in two flavors, Helidon SE and Helidon MP. Both flavors deliver great results for generating cloud-native Java applications built on microservices, but they offer distinct developer experiences.

**Helidon SE** is Helidon’s foundational set of APIs; it offers a lightweight and flexible approach to developing Java applications within a framework. Generally, you should use Helidon SE if you want the greatest possible control over the development and functionality of your application, while still benefiting from the Helidon framework.

**Helidon MP** builds on the APIs in Helidon SE by adding support for [Eclipse MicroProfile](https://projects.eclipse.org/proposals/eclipse-microprofile), a project whose goal is to standardize Enterprise Java APIs for microservices development.

No matter which flavor of Helidon you choose, you will have Helidon WebServer at the
core of your application. In Helidon 4, the WebServer was re-written from the ground up
to be based on virtual threads giving you a server with very high throughput
and a thread-per-request architecture that simplifies development.

<table>
<caption>Which flavor should <em>you</em> use?</caption>
<colgroup>
<col style="width: 50%" />
<col style="width: 50%" />
</colgroup>
<tbody>
<tr>
<td style="text-align: left;"><p><strong>Use Helidon SE if</strong></p></td>
<td style="text-align: left;"><p><strong>Use Helidon MP if</strong></p></td>
</tr>
<tr>
<td style="text-align: left;"><ul>
<li><p>You want full transparency into and greater control over your development process.</p></li>
<li><p>You prefer an imperative, fluent style of API over a declarative style.</p></li>
<li><p>You do not plant to use CDI-based components.</p></li>
<li><p>You want to limit the number of third-party dependencies.</p></li>
<li><p>You prefer a microframework with a tiny footprint (~7 MB).</p></li>
</ul>
<p>Learn more at <a href="../about/../se/introduction.xml">Helidon SE</a></p></td>
<td style="text-align: left;"><ul>
<li><p>You want to take advantage of the APIs provided by the MicroProfile specification.</p></li>
<li><p>You are already familiar with Jakarta EE, MicroProfile, or Spring Boot and want a similar development experience.</p></li>
<li><p>You are migrating existing Jakarta EE applications to microservices architecture.</p></li>
<li><p>You plan to use CDI components or extensions, Jakarta Persistence (JPA) for data access, or Jersey (Jakarta RESTful Web Services, JAX-RS) for RESTful services.</p></li>
</ul>
<p>Learn more at <a href="../about/../mp/introduction.xml">Helidon MP</a></p></td>
</tr>
</tbody>
</table>

Compare the following code examples to see the differences in how you would implement a simple RESTful service using either Helidon SE or Helidon MP.

*Helidon SE sample*

``` java
WebServer.builder()
        .addRouting(HttpRouting.builder()
                            .get("/greet", (req, res)
                                    -> res.send("Hello World!")))
        .build()
        .start();
```

*Helidon MP sample*

``` java
@Path("hello")
public class HelloWorld {
    @GET
    public String hello() {
        return "Hello World";
    }
}
```

## Next Steps

- Read [Get Started](./get_started.md) and learn how to set up your environment.
- Try out Helidon with the Quick Start tutorials:
  - [Helidon SE Quick Start](../se/guides/quickstart.md)
  - [Helidon MP Quick Start](../mp/guides/quickstart.md)
- If you’re using an earlier version of Helidon, read the Helidon Upgrade Guides for guidance on how to move to the latest version:
  - [Helidon SE 4.x Upgrade Guide](../se/guides/upgrade_4x.md)
  - [Helidon MP 4.x Upgrade Guide](../mp/guides/upgrade_4x.md)

Helidon is an open source framework (developed by Oracle) licensed under Apache License 2.0. You can follow its development at the [Helidon GitHub repository](https://github.com/helidon-io/helidon).
