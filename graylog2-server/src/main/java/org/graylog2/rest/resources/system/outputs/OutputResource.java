/**
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.resources.system.outputs;

import com.codahale.metrics.annotation.Timed;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.graylog2.database.NotFoundException;
import org.graylog2.database.ValidationException;
import org.graylog2.outputs.MessageOutputFactory;
import org.graylog2.plugin.streams.Output;
import org.graylog2.rest.documentation.annotations.Api;
import org.graylog2.rest.documentation.annotations.ApiOperation;
import org.graylog2.rest.documentation.annotations.ApiParam;
import org.graylog2.rest.documentation.annotations.ApiResponse;
import org.graylog2.rest.documentation.annotations.ApiResponses;
import org.graylog2.rest.resources.RestResource;
import org.graylog2.rest.resources.streams.outputs.AvailableOutputSummary;
import org.graylog2.security.RestPermissions;
import org.graylog2.streams.OutputService;
import org.graylog2.streams.outputs.CreateOutputRequest;
import org.graylog2.utilities.ConfigurationMapConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
@RequiresAuthentication
@Api(value = "System/Outputs", description = "Manage outputs")
@Path("/system/outputs")
public class OutputResource extends RestResource {
    private static final Logger LOG = LoggerFactory.getLogger(OutputResource.class);

    private final OutputService outputService;
    private final MessageOutputFactory messageOutputFactory;

    @Inject
    public OutputResource(OutputService outputService,
                          MessageOutputFactory messageOutputFactory) {
        this.outputService = outputService;
        this.messageOutputFactory = messageOutputFactory;
    }

    @GET
    @Timed
    @ApiOperation(value = "Get a list of all outputs")
    @RequiresPermissions(RestPermissions.STREAM_OUTPUTS_CREATE)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> get() {
        checkPermission(RestPermissions.OUTPUTS_READ);
        final Set<Output> outputs = outputService.loadAll();

        return new HashMap<String, Object>() {
            {
                put("total", outputs.size());
                put("outputs", outputs);
            }
        };
    }

    @GET @Path("/{outputId}")
    @Timed
    @ApiOperation(value = "Get specific output")
    @RequiresPermissions(RestPermissions.OUTPUTS_CREATE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such output on this node.")
    })
    public Output get(@ApiParam(title = "outputId", description = "The id of the output we want.", required = true) @PathParam("outputId") String outputId) throws NotFoundException {
        checkPermission(RestPermissions.OUTPUTS_READ, outputId);
        return outputService.load(outputId);
    }

    @POST
    @Timed
    @ApiOperation(value = "Create an output")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Invalid output specification in input.")
    })
    public Response create(@ApiParam(title = "JSON body", required = true) CreateOutputRequest csor) throws ValidationException {
        checkPermission(RestPermissions.OUTPUTS_CREATE);
        final AvailableOutputSummary outputSummary = messageOutputFactory.getAvailableOutputs().get(csor.type);

        if (outputSummary == null) {
            throw new ValidationException("type", "Invalid output type");
        }

        // Make sure the config values will be stored with the correct type.
        csor.configuration = ConfigurationMapConverter.convertValues(csor.configuration, outputSummary.requestedConfiguration);

        Output output = outputService.create(csor, getCurrentUser().getName());

        return Response.status(Response.Status.CREATED).entity(output).build();
    }

    @DELETE @Path("/{outputId}")
    @Timed
    @ApiOperation(value = "Delete output")
    @RequiresPermissions(RestPermissions.OUTPUTS_TERMINATE)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such stream/output on this node.")
    })
    public Response delete(@ApiParam(title = "outputId", description = "The id of the output that should be deleted", required = true) @PathParam("outputId") String outputId) throws org.graylog2.database.NotFoundException {
        checkPermission(RestPermissions.OUTPUTS_TERMINATE);
        Output output = outputService.load(outputId);
        outputService.destroy(output);

        return Response.status(Response.Status.OK).build();
    }

    @GET @Path("/available")
    @Timed
    @ApiOperation(value = "Get all available output modules")
    @RequiresPermissions(RestPermissions.STREAMS_READ)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> available() {
        return new HashMap<String, Object>() {
            {
                put("types", messageOutputFactory.getAvailableOutputs());
            }
        };
    }
}
