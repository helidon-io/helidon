# pico-examples-car

This example compares Pico to Jakarta's Hk2. The example is constructed around a virtual library with bookcases, books, and a color wheel on display. This example is different from the other examples in that it combines Hk2 and Pico into the same application. A best practice is to only have one injection framework being used, so this combination is generally not a very realistic example for how a developer would construct their application. Nevertheless, this will be done here in order to convey how the frameworks are similar in some ways while different in other ways. 

Take a momemt to review the code. Note that the generated source code is found under
"./target/generated-sources" for Pico. Generate META-INF is found under "./target/classes".  Similarities and differences are listed below.

# Building and Running
```
> mvn clean install
> ./run.sh
```

# Notable

1. Both Pico and Hk2 supports jakarta.inject and use annotation processors (see [pom.xml](pom.xml)) to process the injection model in compliance to jsr-330 specifications. But the way they work is very different. Hk2 uses compile-time to determine the set of services in the model, and then at runtime will reflectively analyze those classes for resolving injection point dependencies. Pico, on the other hand, relies 100% on compile-time processing that generates source. In Pico the entire application can be analyzed and verified for completeness (i.e., no missing non-optional dependencies) at compile-time instead of Hk2's approach to perform this at runtime lazily during a service activation - and that validation only happens if the service being looked up is missing its dependencies - which might not happen unless your testing and runtime goes down the path of activating a service that is missing its dependencies. In Pico, when the application is created it is completely bound and verified safeguarding against this possibility. This technique of code generation and binding also leads to better performance of Pico as compared to Hk2, and additionally helps ensure deterministic behavior. 

2. The API programming model between Hk2 and Pico is very similar (see the application).

* Declaring contracts. Contracts (usually interface types) are a means to lookup or inject into other classes. In the example, there is a contract called <i>BookHolder</i>. Implementation classes of this include: <i>BookCase, EmptyRedBookCase, GreenBookCase, and Library</i>.

```java
import io.helidon.pico.api.Contract;

@Contract
@org.jvnet.hk2.annotations.Contract
public interface BookHolder {
    Collection<?> getBooks();
}
```

In Hk2's annotation processing the use of <i>Contract</i> or <i>ExternalContract</i> is required to be present. In Pico this is optional when using -Aio.helidon.pico.autoAddNonContractInterfaces=true (see pom).

* Declaring services. Services (usually concrete class types implementing zero or more contracts) can have zero or more injection points using the standard @Inject annotation. In the below example we see how Library is annotated as a <i>Service</i> for Hk2, while not being necessary for Pico. Pico will naturally resolve any standard Singleton scoped (or ApplicationScoped w/ another -A flag) type, or any service type that contains injection points even without having a <i>Scope</i> annotation.

```
@org.jvnet.hk2.annotations.Service
@jakarta.inject.Singleton
@ToString
public class Library implements BookHolder {
    ...
}
```

* Injection points. If we look more closely at the Library class we will see it uses constructor injection. Constructor, setter, and field injection are all supported in both Hk2 and Pico.  Note, however, that Pico can only handle public, protected, or package-privates (i.e., no pure private) types and the only for types that are also non-static.  This is due to the way <i>Activators</i> are code generated to work with your main service classes.

```
@io.helidon.pico.Contract
@org.jvnet.hk2.annotations.Service
@jakarta.inject.Singleton
public class Library implements BookHolder {
   private List<Provider<Book>> books;
   private List<BookHolder> bookHolders;
   private ColorWheel colorWheel;

   @Inject
   public Library(List<Provider<Book>> books, List<BookHolder> bookHolders, ColorWheel colorWheel) {
      this.books = books;
      this.bookHolders = bookHolders;
      this.colorWheel = colorWheel;
   }

   ...
}
```

* Pico can handle injection of <i>T, Provider<T>, Optional<T>, and List<Provider<T>></i> while Hk2 can handle <i>T, Provider<T>, Optional<T>, and IterableProvider<T>.  Notice how Hk2 does not handle List and yet the annotation processor accepted this form of injection.  It is not until runtime that the problem is observed.  This type of issue would be found at compile time in Pico.


```
    public static void main(String[] args) {
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        ServiceLocator locator = ServiceLocatorUtilities.createAndPopulateServiceLocator();

        try {
            ServiceHandle<Library> librarySh = locator.getServiceHandle(Library.class);
            System.out.println("found a library handle: " + librarySh.getActiveDescriptor());
            Library library = librarySh.getService();
            System.out.println("found a library: " + library);
        } catch (Exception e) {
            // list injection is not supported in Hk2 - must switch to use IterableProvider instead.
            // see https://javaee.github.io/hk2/apidocs/org/glassfish/hk2/api/IterableProvider.html
            // and see https://javaee.github.io/hk2/introduction.html
            System.out.println("error: " + e.getMessage());
        }

```

* Output from MainHk2 at runtime:

```
RUN 1: (HK2)
found a library handle: SystemDescriptor(
	implementation=io.helidon.examples.examples.book.Library
	contracts={io.helidon.examples.pico.book.Library,io.helidon.examples.pico.book.BookHolder}
	scope=jakarta.inject.Singleton
	qualifiers={}
	descriptorType=CLASS
	descriptorVisibility=NORMAL
	metadata=
	rank=0
	loader=null
	proxiable=null
	proxyForSameScope=null
	analysisName=null
	id=12
	locatorId=0
	identityHashCode=99347477
	reified=true)
...
error: A MultiException has 4 exceptions.  They are:
1. org.glassfish.hk2.api.UnsatisfiedDependencyException: There was no object available in __HK2_Generated_0 for injection at SystemInjecteeImpl(requiredType=List<Provider<Book>>,parent=Library,qualifiers={},position=0,optional=false,self=false,unqualified=null,940060004)
2. org.glassfish.hk2.api.UnsatisfiedDependencyException: There was no object available in __HK2_Generated_0 for injection at SystemInjecteeImpl(requiredType=List<BookHolder>,parent=Library,qualifiers={},position=1,optional=false,self=false,unqualified=null,1121172875)
3. java.lang.IllegalArgumentException: While attempting to resolve the dependencies of io.helidon.examples.pico.book.Library errors were found
4. java.lang.IllegalStateException: Unable to perform operation: resolve on io.helidon.examples.pico.book.Library
```

* Output from MainPico at runtime:

```
RUN 1: (PICO))
found a library provider: Library$$picoActivator:io.helidon.examples.pico.book.Library:INIT:[io.helidon.examples.pico.book.BookHolder]
...
library is open: Library(books=[MobyDickInBlue$$picoActivator@50134894:io.helidon.examples.pico.book.MobyDickInBlue@0 : INIT : [io.helidon.examples.pico.book.Book], ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]], bookHolders=[BookCase(allBooks=[MobyDickInBlue$$picoActivator@50134894:io.helidon.pico.examples.book.MobyDickInBlue@0 : INIT : [io.helidon.pico.examples.book.Book], ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]]), EmptyRedBookCase(books=[Optional.empty]), GreenBookCase(greenBooks=[ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]])], colorWheel=ColorWheel(preferredOptionalRedThing=Optional[EmptyRedBookCase(books=[Optional.empty])], preferredOptionalGreenThing=Optional[GreenColor()], preferredOptionalBlueThing=Optional[BlueColor()], preferredProviderRedThing=EmptyRedBookCase$$picoActivator@619a5dff:io.helidon.pico.examples.book.EmptyRedBookCase@16b4a017 : ACTIVE : [io.helidon.pico.examples.book.BookHolder, io.helidon.pico.examples.book.Color, io.helidon.pico.examples.book.RedColor], preferredProviderGreenThing=GreenColor$$picoActivator@7506e922:io.helidon.pico.examples.book.GreenColor@8807e25 : ACTIVE : [io.helidon.pico.examples.book.Color], preferredProviderBlueThing=BlueColor$$picoActivator@25f38edc:io.helidon.pico.examples.book.BlueColor@2a3046da : ACTIVE : [io.helidon.pico.examples.book.Color]))
library: Library(books=[MobyDickInBlue$$picoActivator@50134894:io.helidon.pico.examples.book.MobyDickInBlue@0 : INIT : [io.helidon.pico.examples.book.Book], ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]], bookHolders=[BookCase(allBooks=[MobyDickInBlue$$picoActivator@50134894:io.helidon.pico.examples.book.MobyDickInBlue@0 : INIT : [io.helidon.pico.examples.book.Book], ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]]), EmptyRedBookCase(books=[Optional.empty]), GreenBookCase(greenBooks=[ParadiseLostInGreen$$picoActivator@28ba21f3:io.helidon.pico.examples.book.ParadiseLostInGreen@0 : INIT : [io.helidon.pico.examples.book.Book], UlyssesInGreen$$picoActivator@2530c12:io.helidon.pico.examples.book.UlyssesInGreen@0 : INIT : [io.helidon.pico.examples.book.Book]])], colorWheel=ColorWheel(preferredOptionalRedThing=Optional[EmptyRedBookCase(books=[Optional.empty])], preferredOptionalGreenThing=Optional[GreenColor()], preferredOptionalBlueThing=Optional[BlueColor()], preferredProviderRedThing=EmptyRedBookCase$$picoActivator@619a5dff:io.helidon.pico.examples.book.EmptyRedBookCase@16b4a017 : ACTIVE : [io.helidon.pico.examples.book.BookHolder, io.helidon.pico.examples.book.Color, io.helidon.pico.examples.book.RedColor], preferredProviderGreenThing=GreenColor$$picoActivator@7506e922:io.helidon.pico.examples.book.GreenColor@8807e25 : ACTIVE : [io.helidon.pico.examples.book.Color], preferredProviderBlueThing=BlueColor$$picoActivator@25f38edc:io.helidon.pico.examples.book.BlueColor@2a3046da : ACTIVE : [io.helidon.pico.examples.book.Color]))

```

* The output for Pico (see "library is open:") reveals how lifecycle is supported via the use of the standard <i>PostConstruct and PreDestroy</i> annotations. Pico will also display services that have not yet been lazily initialized (see "INIT") vs those services that have been (see "ACTIVE"). Hk2 and Pico behave in similar ways - it will only activate a service when the provider (or ServiceHandle in Hk2) is resolved. Until that time no service will be activated. It is a best practice, therefore, to use Provider<> type injection as much as possible as it will avoid chains of activation at runtime (i.e., every non-provided type injection point will be recursively activated).   

3. Pico generates a suggested <i>module-info.java</i> based upon analysis of your injection/dependency model (see ./target/pico/classes/module-info.java.pico). Hk2 does not have this feature.

```
// @Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
module io.helidon.examples.pico.book {
    exports io.helidon.examples.pico.book;
    // pico module - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    provides io.helidon.pico.Module with io.helidon.examples.pico.book.picoModule;
    // pico services - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    requires transitive io.helidon.pico;
}
```

4. As previously mentioned, both Hk2 and Pico supports <i>PostConstruct and PreDestroy</i> annotations. Additionally, both frameworks offers a notion of <i>RunLevel</i> where RunLevel(value==0) typically represents a "startup" like service.  Check the javadoc for details.

5. Pico can optionally generate the activators (i.e., the DI supporting classes) on an external jar module. See the [logger](../logger) example for details.  Hk2 has a similar mechanism called inhabitants generator - see references below.

# References
* https://javaee.github.io/hk2/introduction.html
* https://javaee.github.io/hk2/getting-started.html
