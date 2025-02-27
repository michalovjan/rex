package org.jboss.pnc.rex.rest.providers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.jboss.pnc.rex.common.exceptions.BadRequestException;
import org.jboss.pnc.rex.dto.responses.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
    @Override
    public Response toResponse(BadRequestException e) {
        log.warn("Invalid request: " + e, e);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
