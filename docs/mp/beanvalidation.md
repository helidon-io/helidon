# Bean Validation Introduction

## Overview

Helidon supports Bean Validation via its integration with JAX-RS/Jersey. The [Jakarta Bean Validation specification](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html) defines an API to validate Java beans. Bean Validation is supported in REST resource classes as well as in regular application beans.

If bean validation is required outside JAX-RS/Jersey use cases, it is also available in Helidon. It follows the standard [Jakarta Bean Validation specification](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html) which defines an API to validate Java beans.

## Maven Coordinates

To enable Bean Validation, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>org.glassfish.jersey.ext</groupId>
    <artifactId>jersey-bean-validation</artifactId>
</dependency>
```

For general validation, please add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.helidon.microprofile.bean-validation</groupId>
    <artifactId>helidon-microprofile-bean-validation</artifactId>
</dependency>
```

## API

The specification defines a small set of built-in constraints. Their usage is encouraged both in regular constraint declarations and as composing constraints. Using this set of constraints will enhance portability of your constraints across constraint-consuming frameworks relying on the metadata API (such as client side validation frameworks or database schema generation frameworks).

Built-in annotations are annotated with an empty `@Constraint` annotation to avoid any dependency between the specification API and a specific implementation. Each Jakarta Bean Validation provider must recognize built-in constraint annotations as valid constraint definitions and provide compliant constraint implementations for each. The built-in constraint validation implementation is having a lower priority than an XML mapping definition. In other words ConstraintValidator implementations for built-in constraints can be overridden by using the XML mapping (see Overriding constraint definitions in XML).

All built-in constraints are in the `jakarta.validation.constraints` package. Here is the list of constraints and their declaration.

<table>
<colgroup>
<col style="width: 28%" />
<col style="width: 71%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Annotation</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>@Null</code></p></td>
<td style="text-align: left;"><p>The annotated element must be <code>null</code>. Accepts any type.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@NotNull</code></p></td>
<td style="text-align: left;"><p>The annotated element must not be <code>null</code>. Accepts any type.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@AssertTrue</code></p></td>
<td style="text-align: left;"><p>The annotated element must be true. Supported types are <code>boolean</code> and <code>Boolean</code>. <code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@AssertFalse</code></p></td>
<td style="text-align: left;"><p>The annotated element must be false. Supported types are <code>boolean</code> and <code>Boolean</code>. <code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Min</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a number whose value must be higher or equal to the specified minimum.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrappers</p></li>
</ul>
<p>Note that <code>double</code> and <code>float</code> are not supported due to rounding errors (some providers might provide some approximative support).</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Max</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a number whose value must be lower or equal to the specified maximum.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrappers</p></li>
</ul>
<p>Note that <code>double</code> and <code>float</code> are not supported due to rounding errors (some providers might provide some approximative support).</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@DecimalMin</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a number whose value must be higher or equal to the specified minimum.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>CharSequence</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrappers</p></li>
</ul>
<p>Note that <code>double</code> and <code>float</code> are not supported due to rounding errors (some providers might provide some approximative support).</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@DecimalMax</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a number whose value must be lower or equal to the specified maximum.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>CharSequence</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrappers</p></li>
</ul>
<p>Note that <code>double</code> and <code>float</code> are not supported due to rounding errors (some providers might provide some approximative support).</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Negative</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a strictly negative number (i.e. 0 is considered as an invalid value).</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrappers</p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@NegativeOrZero</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a negative number or 0.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, <code>float</code>, or <code>double</code> and their respective wrappers</p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Positive</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a strictly positive number (i.e. 0 is considered as an invalid value).</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, <code>float</code>, or <code>double</code> and their respective wrappers</p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@PositiveOrZero</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a positive number or 0.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, <code>float</code>, or <code>double</code> and their respective wrappers</p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Size</code></p></td>
<td style="text-align: left;"><p>The annotated element size must be between the specified boundaries (included). Supported types are: * <code>CharSequence</code> - length of character sequence is evaluated * <code>Collection</code> - collection size is evaluated * <code>Map</code> - map size is evaluated * <code>Array</code> (array length is evaluated)</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Digits</code></p></td>
<td style="text-align: left;"><p>The annotated element must be a number within accepted range.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>BigDecimal</code></p></li>
<li><p><code>BigInteger</code></p></li>
<li><p><code>CharSequence</code></p></li>
<li><p><code>byte</code>, <code>short</code>, <code>int</code>, <code>long</code>, and their respective wrapper types</p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Past</code></p></td>
<td style="text-align: left;"><p>The annotated element must be an instant, date or time in the past. <code>Now</code> is defined by the <code>ClockProvider</code> attached to the <code>Validator</code> or <code>ValidatorFactory</code>. The default <code>clockProvider</code> defines the current time according to the virtual machine, applying the current default time zone if needed.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>java.util.Date</code></p></li>
<li><p><code>java.util.Calendar</code></p></li>
<li><p><code>java.time.Instant</code></p></li>
<li><p><code>java.time.LocalDate</code></p></li>
<li><p><code>java.time.LocalDateTime</code></p></li>
<li><p><code>java.time.LocalTime</code></p></li>
<li><p><code>java.time.MonthDay</code></p></li>
<li><p><code>java.time.OffsetDateTime</code></p></li>
<li><p><code>java.time.OffsetTime</code></p></li>
<li><p><code>java.time.Year</code></p></li>
<li><p><code>java.time.YearMonth</code></p></li>
<li><p><code>java.time.ZonedDateTime</code></p></li>
<li><p><code>java.time.chrono.HijrahDate</code></p></li>
<li><p><code>java.time.chrono.JapaneseDate</code></p></li>
<li><p><code>java.time.chrono.MinguoDate</code></p></li>
<li><p><code>java.time.chrono.ThaiBuddhistDate</code></p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@PastOrPresent</code></p></td>
<td style="text-align: left;"><p>The annotated element must be an instant, date or time in the past or in the present. <code>Now</code> is defined by the <code>ClockProvider</code> attached to the <code>Validator</code> or <code>ValidatorFactory</code>. The default <code>clockProvider</code> defines the current time according to the virtual machine, applying the current default time zone if needed.</p>
<p>The notion of present is defined relatively to the type on which the constraint is used. For instance, if the constraint is on a <code>Year</code>, present would mean the whole current year.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>java.util.Date</code></p></li>
<li><p><code>java.util.Calendar</code></p></li>
<li><p><code>java.time.Instant</code></p></li>
<li><p><code>java.time.LocalDate</code></p></li>
<li><p><code>java.time.LocalDateTime</code></p></li>
<li><p><code>java.time.LocalTime</code></p></li>
<li><p><code>java.time.MonthDay</code></p></li>
<li><p><code>java.time.OffsetDateTime</code></p></li>
<li><p><code>java.time.OffsetTime</code></p></li>
<li><p><code>java.time.Year</code></p></li>
<li><p><code>java.time.YearMonth</code></p></li>
<li><p><code>java.time.ZonedDateTime</code></p></li>
<li><p><code>java.time.chrono.HijrahDate</code></p></li>
<li><p><code>java.time.chrono.JapaneseDate</code></p></li>
<li><p><code>java.time.chrono.MinguoDate</code></p></li>
<li><p><code>java.time.chrono.ThaiBuddhistDate</code></p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Future</code></p></td>
<td style="text-align: left;"><p>The annotated element must be an instant, date or time in the future. <code>Now</code> is defined by the <code>ClockProvider</code> attached to the <code>Validator</code> or <code>ValidatorFactory</code>. The default <code>clockProvider</code> defines the current time according to the virtual machine, applying the current default time zone if needed.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>java.util.Date</code></p></li>
<li><p><code>java.util.Calendar</code></p></li>
<li><p><code>java.time.Instant</code></p></li>
<li><p><code>java.time.LocalDate</code></p></li>
<li><p><code>java.time.LocalDateTime</code></p></li>
<li><p><code>java.time.LocalTime</code></p></li>
<li><p><code>java.time.MonthDay</code></p></li>
<li><p><code>java.time.OffsetDateTime</code></p></li>
<li><p><code>java.time.OffsetTime</code></p></li>
<li><p><code>java.time.Year</code></p></li>
<li><p><code>java.time.YearMonth</code></p></li>
<li><p><code>java.time.ZonedDateTime</code></p></li>
<li><p><code>java.time.chrono.HijrahDate</code></p></li>
<li><p><code>java.time.chrono.JapaneseDate</code></p></li>
<li><p><code>java.time.chrono.MinguoDate</code></p></li>
<li><p><code>java.time.chrono.ThaiBuddhistDate</code></p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@FutureOrPresent</code></p></td>
<td style="text-align: left;"><p>The annotated element must be an instant, date or time in the present or in the future. <code>Now</code> is defined by the <code>ClockProvider</code> attached to the <code>Validator</code> or <code>ValidatorFactory</code>. The default <code>clockProvider</code> defines the current time according to the virtual machine, applying the current default time zone if needed.</p>
<p>The notion of present here is defined relatively to the type on which the constraint is used. For instance, if the constraint is on a <code>Year</code>, present would mean the whole current year.</p>
<p>Supported types are:</p>
<ul>
<li><p><code>java.util.Date</code></p></li>
<li><p><code>java.util.Calendar</code></p></li>
<li><p><code>java.time.Instant</code></p></li>
<li><p><code>java.time.LocalDate</code></p></li>
<li><p><code>java.time.LocalDateTime</code></p></li>
<li><p><code>java.time.LocalTime</code></p></li>
<li><p><code>java.time.MonthDay</code></p></li>
<li><p><code>java.time.OffsetDateTime</code></p></li>
<li><p><code>java.time.OffsetTime</code></p></li>
<li><p><code>java.time.Year</code></p></li>
<li><p><code>java.time.YearMonth</code></p></li>
<li><p><code>java.time.ZonedDateTime</code></p></li>
<li><p><code>java.time.chrono.HijrahDate</code></p></li>
<li><p><code>java.time.chrono.JapaneseDate</code></p></li>
<li><p><code>java.time.chrono.MinguoDate</code></p></li>
<li><p><code>java.time.chrono.ThaiBuddhistDate</code></p></li>
</ul>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Pattern</code></p></td>
<td style="text-align: left;"><p>The annotated <code>CharSequence</code> must match the specified regular expression. The regular expression follows the Java regular expression conventions see <code>java.util.regex.Pattern</code>. Accepts <code>CharSequence</code>.</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@NotEmpty</code></p></td>
<td style="text-align: left;"><p>The annotated element must not be <code>null</code> nor empty. Supported types are: * <code>CharSequence</code> - length of character sequence is evaluated * <code>Collection</code> - collection size is evaluated * <code>Map</code> - map size is evaluated * <code>Array</code> (array length is evaluated)</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@NotBlank</code></p></td>
<td style="text-align: left;"><p>The annotated element must not be <code>null</code> and must contain at least one non-whitespace character. Accepts <code>CharSequence</code>.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>@Email</code></p></td>
<td style="text-align: left;"><p>The string has to be a well-formed email address. Exact semantics of what makes up a valid email address are left to Jakarta Bean Validation providers. Accepts <code>CharSequence</code>.</p>
<p><code>Null</code> elements are considered valid.</p></td>
</tr>
</tbody>
</table>

## Configuration

Bean Validation can be configured using `META-INF/validation.xml`.

For more information about configuring the validator factory in validation.xml, see [Hibernate Validator Documentation](https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/?v=7.0#chapter-xml-configuration).

## Examples

1.  The following example shows a simple resource method annotated with `@POST` whose parameter must be *not null* and *valid*. Validating a parameter in this case implies making sure that any constraint annotations in the `Greeting` class are satisfied. The resource method shall never be called if the validation fails, with a 400 (Bad Request) status code returned instead.

    ``` java
    @Path("helloworld")
    public class HelloWorld {

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        public void post(@NotNull @Valid Greeting greeting) {
            // ...
        }
    }
    ```

2.  The following example shows a simple application with one field declared as *not null* using `@NotNull` annotation:

    ``` java
    public class GreetingHolder {
        @NotNull
        private String greeting;
    }
    ```

    If the bean contains a method parameter annotated with @Valid, and GreetingHolder with *null_greeting is passed, then a \_ValidationException* will be thrown:

    ``` java
    @ApplicationScoped
    public class GreetingProvider {
        private GreetingHolder greetingHolder;

        void setGreeting(@Valid GreetingHolder greetingHolder) {
            this.greetingHolder = greetingHolder;
        }
    }
    ```

    > [!NOTE]
    > `beans.xml` is required to identify beans and for bean validation to work properly.

Examples are available in [our official GitHub repository](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/microprofile/bean-validation).

## Additional Information

Helidon uses [Hibernate Bean Validator](https://hibernate.org/validator/) for general bean validation.

## Reference

- [Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html)
