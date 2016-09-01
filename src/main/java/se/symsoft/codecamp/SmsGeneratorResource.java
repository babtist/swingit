/*
 * Copyright Symsoft AB 1996-2015. All Rights Reserved.
 */
package se.symsoft.codecamp;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import se.symsoft.cc2016.logutil.Logged;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import java.net.URISyntaxException;
import java.util.UUID;

@Path("generators")
public class SmsGeneratorResource {

    @Inject
    DynamoDBMapper dynamoDB;


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void list(@Suspended final AsyncResponse asyncResponse) {
        PaginatedScanList<GeneratorData> pageList = dynamoDB.scan(GeneratorData.class, new DynamoDBScanExpression());
        asyncResponse.resume(pageList);
    }

    @GET
    @Path("ping")
    public void ping(@Suspended final AsyncResponse asyncResponse) {
        asyncResponse.resume("Pong");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Logged
    public void create(@Suspended final AsyncResponse asyncResponse, final GeneratorData data) throws URISyntaxException {
        data.setId(UUID.randomUUID());
        data.setState(GeneratorData.STATE_IDLE);
        try {
            dynamoDB.save(data);
            SmsGenerator generator = new SmsGenerator(data, dynamoDB);
            new Thread(generator::start).start();
            asyncResponse.resume(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Logged
    public void update(@Suspended final AsyncResponse asyncResponse, final GeneratorData data) throws URISyntaxException {
        try {
            final GeneratorData generatorData = dynamoDB.load(GeneratorData.class, data.getId());
            if (generatorData == null) {
                asyncResponse.resume(new NotFoundException("Entity with id = " + data.getId() + " not found"));
                return;
            }
            data.setState(generatorData.getState());
            dynamoDB.save(data);

            asyncResponse.resume(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/start")
    @Logged
    public void start(@Suspended final AsyncResponse asyncResponse, @PathParam("id") UUID id) throws URISyntaxException {
        try {
            final GeneratorData data = dynamoDB.load(GeneratorData.class, id);
            if (data == null) {
                asyncResponse.resume(new NotFoundException("Entity with id = " + id + " not found"));
                return;
            }
            if (data.getState() == GeneratorData.STATE_RUNNING) {
                asyncResponse.resume(new NotAcceptableException("Generator is already running"));
                return;
            }
            data.setState(GeneratorData.STATE_RUNNING);
            data.setSuccess(0);
            data.setFailed(0);
            dynamoDB.save(data);
            SmsGenerator generator = new SmsGenerator(data, dynamoDB);
            new Thread(generator::start).start();
            asyncResponse.resume(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/stop")
    @Logged
    public void stop(@Suspended final AsyncResponse asyncResponse, @PathParam("id") UUID id) throws URISyntaxException {
        try {
            final GeneratorData data = dynamoDB.load(GeneratorData.class, id);
            if (data == null) {
                asyncResponse.resume(new NotFoundException("Entity with id = " + id + " not found"));
                return;
            }
            if (data.getState() == GeneratorData.STATE_RUNNING) {
                // Trying to stop an idle generator
                data.setState(GeneratorData.STATE_STOPPING);
                dynamoDB.save(data);
            }
            asyncResponse.resume(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    public void delete(@Suspended final AsyncResponse asyncResponse, @PathParam("id") UUID id) {
        try {
            final GeneratorData generatorData = dynamoDB.load(GeneratorData.class, id);
            if (generatorData != null) {
                dynamoDB.delete(generatorData);
                asyncResponse.resume(generatorData);
            } else {
                asyncResponse.resume(new NotFoundException("Entity with id = " + id + " not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
