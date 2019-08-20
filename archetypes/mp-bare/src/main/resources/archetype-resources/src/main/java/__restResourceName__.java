package ${package};

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("${restResourcePath}")
public class ${restResourceName} {

    @Path("/")
    @GET
    public String get() {
        return "It works!";
    }
}
