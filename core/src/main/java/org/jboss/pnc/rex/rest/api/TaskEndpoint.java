package org.jboss.pnc.rex.rest.api;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.pnc.rex.dto.TaskDTO;
import org.jboss.pnc.rex.dto.requests.CreateGraphRequest;
import org.jboss.pnc.rex.dto.responses.ErrorResponse;
import org.jboss.pnc.rex.dto.responses.TaskSetResponse;
import org.jboss.pnc.rex.rest.parameters.TaskFilterParameters;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Set;

import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.CONFLICTED_CODE;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.CONFLICTED_DESCRIPTION;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.INVALID_CODE;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.INVALID_DESCRIPTION;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.NOT_FOUND_CODE;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.NOT_FOUND_DESCRIPTION;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.SERVER_ERROR_CODE;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.SERVER_ERROR_DESCRIPTION;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.SUCCESS_CODE;
import static org.jboss.pnc.rex.rest.openapi.OpenapiConstants.SUCCESS_DESCRIPTION;

@Tag(name = "Task endpoint")
@Path("/rest/tasks")
public interface TaskEndpoint {

    String TASK_ID = "Unique identifier of the task";

    @Operation(description = "This endpoint schedules graph of tasks. \n" +
            " The request has a regular graph structure with edges and vertices. \n" +
            " The tasks in edges are identified by their ID and can be either tasks EXISTING or NEW tasks referenced in vertices. " +
            " Therefore, you can add an edge between already existing tasks, new tasks or between an existing task and new task referenced in vertices. " +
            " Adding an edge where the dependant is running or has finished will result in failure. \n" +
            " The tasks in vertices have to be strictly NEW tasks and referencing EXISTING ones will result in failure. \n",
            summary = "An endpoint for starting a graph of tasks.")
    @APIResponses(value = {
            @APIResponse(responseCode = SUCCESS_CODE, description = SUCCESS_DESCRIPTION,
                content = @Content(schema = @Schema(implementation = TaskSetResponse.class))),
            @APIResponse(responseCode = INVALID_CODE, description = INVALID_DESCRIPTION,
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = SERVER_ERROR_CODE, description = SERVER_ERROR_DESCRIPTION,
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Set<TaskDTO> start(@Valid @NotNull CreateGraphRequest request);

    @Operation(summary = "Returns list of all tasks with optional filtering.")
    @APIResponses(value = {
            @APIResponse(responseCode = SUCCESS_CODE, description = SUCCESS_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = TaskSetResponse.class))),
            @APIResponse(responseCode = INVALID_CODE, description = INVALID_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = CONFLICTED_CODE, description = CONFLICTED_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = SERVER_ERROR_CODE, description = SERVER_ERROR_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Set<TaskDTO> getAll(@BeanParam TaskFilterParameters filterParameters);

    @Path("/{taskID}")
    @Operation(summary = "Returns a specific task.")
    @APIResponses(value = {
            @APIResponse(responseCode = SUCCESS_CODE, description = SUCCESS_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = TaskSetResponse.class))),
            @APIResponse(responseCode = NOT_FOUND_CODE, description = NOT_FOUND_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = SERVER_ERROR_CODE, description = SERVER_ERROR_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    TaskDTO getSpecific(@Parameter(description = TASK_ID) @PathParam("taskID") @NotBlank String taskID);

    @Path("/{taskID}/cancel")
    @Operation(summary = "Cancels execution of a task and the tasks which depend on it")
    @APIResponses(value = {
            @APIResponse(responseCode = SUCCESS_CODE, description = SUCCESS_DESCRIPTION),
            @APIResponse(responseCode = INVALID_CODE, description = INVALID_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = SERVER_ERROR_CODE, description = SERVER_ERROR_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PUT
    void cancel(@Parameter(description = TASK_ID) @PathParam("taskID") @NotBlank String taskID);

  /*  @Path("/{serviceName}/graph")
    @APIResponses(value = {
            @APIResponse(responseCode = SUCCESS_CODE, description = SUCCESS_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ServiceListResponse.class))),
            @APIResponse(responseCode = NOT_FOUND_CODE, description = NOT_FOUND_DESCRIPTION),
            @APIResponse(responseCode = SERVER_ERROR_CODE, description = SERVER_ERROR_DESCRIPTION,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<TaskDTO> getGraph(@Parameter(description = SERVICE_NAME) @PathParam("serviceName") String serviceName);*/
}
