package nl.tudelft.ewi.build.jaxrs;

import java.util.UUID;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.builds.BuildManager;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedBuildRequest;
import nl.tudelft.ewi.build.jaxrs.filters.RequireAuthentication;

@Slf4j
@Path("api/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BuildsResource {

	private final BuildManager manager;

	@Inject
	public BuildsResource(BuildManager manager) {
		this.manager = manager;
	}

	@POST
	@RequireAuthentication
	public Response onBuildRequest(@Valid AdaptedBuildRequest buildRequest) {
		try {
			manager.schedule(buildRequest);
			return Response.ok("You're cool").build();
		}
		catch (Throwable e) {
			log.error(e.getMessage(), e);
		}

		return Response.status(Status.CONFLICT)
			.entity("Server cannot accept build request.")
			.build();
	}
	
	@DELETE
	@RequireAuthentication
	@Path("{buildId}")
	public void killBuild(@PathParam("buildId") UUID buildId) {
		manager.killBuild(buildId);
	}

}
