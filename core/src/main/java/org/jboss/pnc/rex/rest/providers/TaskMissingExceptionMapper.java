package org.jboss.pnc.rex.rest.providers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.jboss.pnc.rex.common.exceptions.TaskMissingException;
import org.jboss.pnc.rex.dto.responses.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class TaskMissingExceptionMapper implements ExceptionMapper<TaskMissingException> {
    @Override
    public Response toResponse(TaskMissingException e) {
        Response.Status status = Response.Status.BAD_REQUEST;
        log.warn("Task missing in critical moment: " + e, e);
        return Response.status(status)
                .entity(new ErrorResponse(e))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
