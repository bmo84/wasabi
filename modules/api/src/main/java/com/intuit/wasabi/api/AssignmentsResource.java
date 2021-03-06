/*******************************************************************************
 * Copyright 2016 Intuit
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.intuit.wasabi.api;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.intuit.wasabi.assignment.Assignments;
import com.intuit.wasabi.assignmentobjects.Assignment;
import com.intuit.wasabi.assignmentobjects.SegmentationProfile;
import com.intuit.wasabi.assignmentobjects.User;
import com.intuit.wasabi.authenticationobjects.UserInfo;
import com.intuit.wasabi.authenticationobjects.UserInfo.Username;
import com.intuit.wasabi.authorization.Authorization;
import com.intuit.wasabi.exceptions.AssignmentNotFoundException;
import com.intuit.wasabi.experimentobjects.*;
import com.intuit.wasabi.experimentobjects.Bucket.Label;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.HttpStatus;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;
import static com.intuit.wasabi.api.APISwaggerResource.*;
import static com.intuit.wasabi.assignmentobjects.Assignment.Status.EXPERIMENT_EXPIRED;
import static com.intuit.wasabi.authorizationobjects.Permission.CREATE;
import static java.lang.Boolean.FALSE;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * API endpoint for managing assignments
 */
@Path("/v1/assignments")
@Produces(APPLICATION_JSON)
@Singleton
@Api(value = "Assignments (Submit-Generate user(customer) bucket assignments)")
public class AssignmentsResource {

    private static final Logger LOGGER = getLogger(AssignmentsResource.class);
    private final HttpHeader httpHeader;
    private final Assignments assignments;
    private Authorization authorization;

    @Inject
    AssignmentsResource(final Assignments assignments, final HttpHeader httpHeader, Authorization authorization) {
        this.assignments = assignments;
        this.httpHeader = httpHeader;
        this.authorization = authorization;
    }

    /**
     * Returns a bucket assignment (bucket label) for the specified user within the context of
     * a specific application and experiment, if the user is chosen to be assigned to the experiment based on the
     * probability of sampling percent. Otherwise returns a null assignment for the specified user.
     *
     * By default, creates the bucket assignment if one does not exist. Otherwise set {@code createAssignment} to false.
     *
     * @param applicationName       the unique application id
     * @param experimentLabel       the experiment label, unique within the context
     *                              of an application
     * @param userID                the unique user id
     * @param context               the context string
     * @param createAssignment      the flag to create the experiment if one does not exists
     * @param ignoreSamplingPercent the flag if the sampling percentage should be ignored
     * @param headers               the authorization headers
     * @return Response object
     * bucket payload
     */
    @GET
    @Path("applications/{applicationName}/experiments/{experimentLabel}/users/{userID}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Return bucket assignment for a user",
            notes = "Generate the assignment first if the user has no assignment " +
                    "for this experiment.  Return null if the user is not in the experiment.")
    @Timed
    public Response getAssignment(
                                    @PathParam("applicationName")
                                    @ApiParam(value = "Application Name")
                                    final Application.Name applicationName,

                                    @PathParam("experimentLabel")
                                    @ApiParam(value = "Experiment Label")
                                    final Experiment.Label experimentLabel,

                                    @PathParam("userID")
                                    @ApiParam(value = "User(customer) ID")
                                    final User.ID userID,

                                    @QueryParam("context")
                                    @DefaultValue("PROD")
                                    @ApiParam(value = "context for the experiment, e.g. PROD, QA")
                                    final Context context,

                                    @QueryParam("createAssignment")
                                    @DefaultValue("true")
                                    @ApiParam(value = "whether an assignment should be generated if one doesn't exist",
                                            defaultValue = "true")
                                    final Boolean createAssignment,

                                    @QueryParam("ignoreSamplingPercent")
                                    @DefaultValue("false")
                                    @ApiParam(value = "whether the sampling percent for the experiment should be ignored, " +
                                            "forcing the user into the experiment (if eligible)",
                                            defaultValue = "false")
                                    final Boolean ignoreSamplingPercent,

                                    @javax.ws.rs.core.Context
                                    final HttpHeaders headers) {
    	if ( LOGGER.isDebugEnabled()) {
    		LOGGER.debug("getAssignment userID={}, applicationName={}, experimentLabel={}, context={}, createAssignment={}" +
                                    ", ignoreSamplingPercent={}, headers={}", userID, applicationName, experimentLabel, context, createAssignment,
                                    ignoreSamplingPercent, headers);
    	}
    	
    	Assignment assignment = getAssignment(userID, applicationName, experimentLabel, context, createAssignment,
                ignoreSamplingPercent, null, headers);

        return httpHeader.headers().entity(toMap(assignment)).build();
    }

    private Assignment getAssignment(final User.ID userID, final Application.Name applicationName,
                                     final Experiment.Label experimentLabel, final Context context,
                                     final boolean createAssignment, final boolean ignoreSamplingPercent,
                                     final SegmentationProfile segmentationProfile, final HttpHeaders headers) {
        Assignment assignment = assignments.getSingleAssignment(userID, applicationName, experimentLabel, context,
                createAssignment, ignoreSamplingPercent, segmentationProfile, headers, null);

        // This should not happen when createAssignment == true
        if (assignment == null) {
            throw new AssignmentNotFoundException(userID, applicationName, experimentLabel);
        }

        return assignment;
    }

    /**
     * Same as {@link Assignment}, but the user is chosen to be assigned to the experiment based on 1) profile
     * eligibility and 2) probability of sampling percent. Otherwise returns a null assignment for the specified user.
     *
     * @param applicationName       the unique application id
     * @param experimentLabel       the experiment label, unique within the context
     *                              of an application
     * @param userID                the unique user id
     * @param createAssignment      the flag to create the experiment if one does not exists
     * @param ignoreSamplingPercent the flag if the sampling percentage should be ignored
     * @param context               the context string
     * @param segmentationProfile   the {@link com.intuit.wasabi.assignmentobjects.SegmentationProfile} object
     * @param headers               the authorization headers
     * @return Response object
     */
    @POST
    @Path("applications/{applicationName}/experiments/{experimentLabel}/users/{userID}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Return bucket assignment for a user",
            notes = "Generate the assignment first if the user has no assignment for this experiment. " +
                    "Forces the user to be in the experiment (if eligible based on profile).")
    @Timed
    public Response postAssignment(
                                    @PathParam("applicationName")
                                    @ApiParam(value = "Application Name")
                                    final Application.Name applicationName,

                                    @PathParam("experimentLabel")
                                    @ApiParam(value = "Experiment Label")
                                    final Experiment.Label experimentLabel,

                                    @PathParam("userID")
                                    @ApiParam(value = "User(customer) ID")
                                    final User.ID userID,

                                    @QueryParam("createAssignment")
                                    @DefaultValue("true")
                                    @ApiParam(value = "whether an assignment should be generated if one doesn't exist",
                                            defaultValue = "true")
                                    final Boolean createAssignment,

                                    @QueryParam("ignoreSamplingPercent")
                                    @DefaultValue("false")
                                    @ApiParam(value = "whether the sampling percent for the experiment should be ignored, " +
                                            "forcing the user into the experiment (if eligible)",
                                            defaultValue = "false")
                                    final Boolean ignoreSamplingPercent,

                                    @QueryParam("context")
                                    @DefaultValue("PROD")
                                    @ApiParam(value = "context for the experiment, e.g. PROD, QA")
                                    final Context context,

                                    @ApiParam(name = "segmentationProfile", value = "Segmentation Profile")
                                    final SegmentationProfile segmentationProfile,

                                    @javax.ws.rs.core.Context
                                    final HttpHeaders headers) {
    	if ( LOGGER.isDebugEnabled()) {
    		LOGGER.debug("postAssignment userID={}, applicationName={}, experimentLabel={}, context={}, createAssignment={}" +
                                    ", ignoreSamplingPercent={}, segmentationProfile={}, headers={}", userID, applicationName, 
                                    experimentLabel, context, createAssignment,
                                    ignoreSamplingPercent, segmentationProfile, headers);
    	}

    	Assignment assignment = getAssignment(userID, applicationName, experimentLabel, context, createAssignment,
                ignoreSamplingPercent, segmentationProfile, headers);

        return httpHeader.headers().entity(toMap(assignment)).build();
    }

    /**
     * Returns a bucket assignment for the specified user within the context of
     * one application and several experiments of an application
     *
     * @param applicationName the application name
     * @param userID          the current user id
     * @param context         the context string
     * @param createAssignment          the boolean flag to create
     * @param experimentBatch the experiment batch expone, exptwo
     * @param headers         the authorization headers
     * @return Response object
     */
    @POST
    @Path("applications/{applicationName}/users/{userID}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Return bucket assignments for a user across multiple experiments",
            notes = "Generate the assignments first if the user has no assignment for the specified experiments if the " +
                    "CREATE flag is set to true and the user is eligible with respect to the segmentation profile. " +
                    "Return null if the user is not in the experiment.")
    public Response getBatchAssignments(@PathParam("applicationName")
                                        @ApiParam(value = "Application Name")
                                        final Application.Name applicationName,

                                        @PathParam("userID")
                                        @ApiParam(value = "User(customer) ID")
                                        final User.ID userID,

                                        @QueryParam("context")
                                        @DefaultValue("PROD")
                                        @ApiParam(value = "context for the experiment, eg QA, PROD")
                                        final Context context,

                                        @QueryParam("create")
                                        @DefaultValue("true")
                                        final Boolean createAssignment,

                                        @ApiParam(required = true, defaultValue = DEFAULT_LABELLIST)
                                        final ExperimentBatch experimentBatch,

                                        @javax.ws.rs.core.Context
                                        final HttpHeaders headers) {
    	if ( LOGGER.isDebugEnabled()) {
    		LOGGER.debug("getBatchAssignment userID={}, applicationName={}, context={}, createAssignment={}" +
                                    ", headers={}, experimentBatch={}", userID, applicationName, context, createAssignment,
                                    headers, experimentBatch);
    	}
    	
        List<Map> myAssignments = assignments.doBatchAssignments(userID, applicationName, context, createAssignment, FALSE,
                headers, experimentBatch, null, null);

        return httpHeader.headers().entity(ImmutableMap.<String, Object>builder().put("assignments", myAssignments).build()).build();
    }

    /**
     * Specify a bucket assignment for the specified user within the context of
     * a specific application and experiment.
     *
     * Cannot use when the experiment is in DRAFT state because the buckets may change.
     *
     * @param applicationName the unique application id
     * @param experimentLabel the experiment label, unique within the context
     *                        of an application
     * @param userID          the unique user id
     * @param submittedData   the data submitted by user
     * @param context         the context string
     * @return Response object
     */
    @PUT
    @Path("applications/{applicationName}/experiments/{experimentLabel}/users/{userID}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    /*@ApiOperation(value = "Set a bucket assignment for a user",
            notes = "Set assignment to null if the user is not in the experiment.")*/
    @Timed
    public Response updateAssignment(
                                    @PathParam("applicationName")
                                    @ApiParam(value = "Application Name")
                                    final Application.Name applicationName,

                                    @PathParam("experimentLabel")
                                    @ApiParam(value = "Experiment Label")
                                    final Experiment.Label experimentLabel,

                                    @PathParam("userID")
                                    @ApiParam(value = "User ID")
                                    final User.ID userID,

                                    @ApiParam(value = "Submitted Data")
                                    final Map<String, Object> submittedData,

                                    @QueryParam("context")
                                    @DefaultValue("PROD")
                                    @ApiParam(value = "context for the experiment, eg \"QA\", \"PROD\"")
                                    final Context context) {
    	if ( LOGGER.isDebugEnabled()) {
    		LOGGER.debug("udpateAssignment userID={}, applicationName={}, experimentLabel={}, context={}, "
    				+ "submittedDatat={}", userID, applicationName, experimentLabel, context, submittedData);
    	}
    	
        if (submittedData == null) {
            throw new IllegalArgumentException("Assignment JSON not found in request body");
        }

        Label submittedLabel;
        boolean overwrite;

        if (submittedData.containsKey("assignment")) {
            String submittedAssignment = (String) submittedData.get("assignment");

            // Bucket.Label constructor doesn't accept a null String (indicating assignment out of the experiment).
            // So we have to handle that case by explicitly setting submittedLabel to null.
            submittedLabel = submittedAssignment != null ? Label.valueOf(submittedAssignment) : null;
        } else {
            throw new IllegalArgumentException("Request entity JSON must contain an \"assignment\" property");
        }

        overwrite = submittedData.containsKey("overwrite") && (boolean) submittedData.get("overwrite");

        Assignment response = assignments.putAssignment(userID, applicationName, experimentLabel, context,
                submittedLabel, overwrite);

        return httpHeader.headers().entity(toMap(response)).build();
    }

    /**
     * Create/Retrieve assignments for a single user for experiments associated to a single page
     *
     * @param applicationName       Application Name
     * @param pageName              Page Name
     * @param userID                User Id
     * @param createAssignment      Creates Assignment if set to true, default true
     * @param ignoreSamplingPercent Forces USer into experiment if set to true, default false
     * @param context               Environment Context
     * @param headers               Headers
     * @return Response object
     */
    @GET
    @Path("applications/{applicationName}/pages/{pageName}/users/{userID}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Return bucket assignments for a user for all the experiments associated with a page",
            notes = "If you want to pass segmentation information, use the POST-Call for this method")
    @Timed
    public Response getBatchAssignmentForPage(
                                            @PathParam("applicationName")
                                            @ApiParam(value = "Application Name")
                                            final Application.Name applicationName,

                                            @PathParam("pageName")
                                            @ApiParam(value = "Page Name")
                                            final Page.Name pageName,

                                            @PathParam("userID")
                                            @ApiParam(value = "User(customer) ID")
                                            final User.ID userID,

                                            @QueryParam("createAssignment")
                                            @DefaultValue("true")
                                            @ApiParam(value = "conditional to generate an assignment if one doesn't exist",
                                                    defaultValue = "true")
                                            final boolean createAssignment,

                                            @QueryParam("ignoreSamplingPercent")
                                            @DefaultValue("false")
                                            @ApiParam(value = "whether the sampling percent for the experiment should be ignored, " +
                                                    "forcing the user into the experiment (if eligible)",
                                                    defaultValue = "false")
                                            final boolean ignoreSamplingPercent,

                                            @QueryParam("context")
                                            @DefaultValue("PROD")
                                            @ApiParam(value = "context for the experiment, eg QA, PROD")
                                            final Context context,

                                            @javax.ws.rs.core.Context
                                            HttpHeaders headers) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getBatchAssignmentsForPage applicationName={}, pageName={}, userID={}, context={}, createAssignment={}" +
                                ", ignoreSamplingPercent={}, headers={}", applicationName, pageName, userID, context, createAssignment,
                        ignoreSamplingPercent, headers);
            }

            List<Map> assignmentsFromPage = assignments.doPageAssignments(applicationName, pageName, userID, context,
                    createAssignment, ignoreSamplingPercent, headers, null);

            return httpHeader.headers()
                    .entity(ImmutableMap.<String, Object>builder().put("assignments", assignmentsFromPage).build()).build();
        } catch (Exception e) {
            LOGGER.error("Exception happened while batch-assignment [GET]...", e);
            throw e;
        }
    }

    /**
     * Create/Retrieve assignments for a single user for experiments associated to a single page
     *
     * @param applicationName       Application Name
     * @param pageName              Page Name
     * @param userID                User Id
     * @param createAssignment      createAssignment boolean true will create an assignment
     * @param ignoreSamplingPercent If true, will force user into experiment, default false
     * @param context               Environment context
     * @param segmentationProfile   the {@link com.intuit.wasabi.assignmentobjects.SegmentationProfile} object
     * @param headers               Headers
     * @return Response object which is List of Assignments for user for experiment of the page.
     */
    @POST
    @Path("applications/{applicationName}/pages/{pageName}/users/{userID}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Return bucket assignments for a user for all the experiments associated with a page",
            notes = "The mutual exclusion and segmentation rules apply")
    @Timed
    public Response postBatchAssignmentForPage(
                                            @PathParam("applicationName")
                                            @ApiParam(value = "Application Name")
                                            final Application.Name applicationName,

                                            @PathParam("pageName")
                                            @ApiParam("Page Name")
                                            Page.Name pageName,

                                            @PathParam("userID")
                                            @ApiParam(value = "User(customer) ID")
                                            final User.ID userID,

                                            @QueryParam("createAssignment")
                                            @DefaultValue("true")
                                            @ApiParam(value = "conditional to generate an assignment if one doesn't exist",
                                                    defaultValue = "true")
                                            final boolean createAssignment,

                                            @QueryParam("ignoreSamplingPercent")
                                            @DefaultValue("false")
                                            @ApiParam(value = "whether the sampling percent for the experiment should be ignored, " +
                                                    "forcing the user into the experiment (if eligible)",
                                                    defaultValue = "false")
                                            final boolean ignoreSamplingPercent,

                                            @QueryParam("context")
                                            @DefaultValue("PROD")
                                            @ApiParam(value = "context for the experiment, eg QA, PROD")
                                            final Context context,

                                            @ApiParam(value = "Segmentation Profile")
                                            final SegmentationProfile segmentationProfile,

                                            @javax.ws.rs.core.Context final HttpHeaders headers) {
        try {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("postBatchAssignmentForPage applicationName={}, pageName={}, userID={}, context={}, createAssignment={}" +
                                ", ignoreSamplingPercent={}, headers={}, segmentationProfile={}", applicationName,
                        pageName, userID, context, createAssignment, ignoreSamplingPercent, headers, segmentationProfile);
            }

            List<Map> assignmentsFromPage = assignments.doPageAssignments(applicationName, pageName, userID, context,
                    createAssignment, ignoreSamplingPercent, headers, segmentationProfile);

            return httpHeader.headers()
                    .entity(ImmutableMap.<String, Object>builder().put("assignments", assignmentsFromPage).build()).build();
        } catch (Exception e) {
            LOGGER.error("Exception happened while batch-assignment [GET]...", e);
            throw e;
        }
    }

    /**
     * Tests if the profile parameters passed would satisfy the rule in the experiment specified.
     *
     * @param applicationName     the application name
     * @param experimentLabel     the experiment label
     * @param context             the context string
     * @param segmentationProfile the {@link com.intuit.wasabi.assignmentobjects.SegmentationProfile} object
     * @param headers             the authorization headers
     * @return Response object contains the rules fit with the requested parameters
     */
    @POST
    @Path("applications/{applicationName}/experiments/{experimentLabel}/ruletest")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Test the segmentation rule of an experiment")
    @Timed
    public Response postAssignmentRuleTest(
                                            @PathParam("applicationName")
                                            @ApiParam(value = "Application Name")
                                            final Application.Name applicationName,

                                            @PathParam("experimentLabel")
                                            @ApiParam(value = "Experiment Label")
                                            final Experiment.Label experimentLabel,

                                            @QueryParam("context")
                                            @DefaultValue("PROD")
                                            @ApiParam(value = "context for the experiment, eg QA, PROD")
                                            final Context context,

                                            @ApiParam(name = "segmentationProfile", value = "Segmentation Profile")
                                            final SegmentationProfile segmentationProfile,

                                            @javax.ws.rs.core.Context
                                            final HttpHeaders headers) {
        boolean ruleResult = assignments.doSegmentTest(applicationName, experimentLabel, context, segmentationProfile,
                headers);

        return httpHeader.headers().entity(ImmutableMap.<String, Object>builder().put("result", ruleResult).build()).build();
    }

    /**
     * Get the length of the assignments queue
     *
     * @return Response object
     */
    @GET
    @Path("queueLength")
    @Produces(APPLICATION_JSON)
    public Response getAssignmentsQueueLength() {
        return httpHeader.headers().entity(assignments.queuesLength()).build();
    }

    /**
     * Get the length of the assignments queue
     *
     * @return Response object
     */
    @GET
    @Path("queueDetails")
    @Produces(APPLICATION_JSON)
    public Response getAssignmentsQueueDetails() {
        return httpHeader.headers().entity(assignments.queuesDetails()).build();
    }

    /**
     * Flush all active and queued messages from the ingestion queues.
     *
     * @return Response object
     */
    @POST
    @Path("flushMessages")
    @Produces(APPLICATION_JSON)
    public Response flushMessages(
            @HeaderParam(AUTHORIZATION) @ApiParam(value = EXAMPLE_AUTHORIZATION_HEADER, required = true) final String authorizationHeader) {
        Username userName = authorization.getUser(authorizationHeader);
        authorization.checkSuperAdmin(userName);
        assignments.flushMessages();
        return httpHeader.headers(HttpStatus.SC_NO_CONTENT).build();
    }

    private Map<String, Object> toMap(final Assignment assignment) {
        Map<String, Object> response = newHashMap();

        // Only include `assignment` property if there is a definitive assignment, either to a bucket or not
        if (assignment.getStatus().isCacheable() && assignment.getStatus() != EXPERIMENT_EXPIRED) {
            response.put("assignment",
                    assignment.getBucketLabel() != null ? assignment.getBucketLabel().toString() : null);

            if (assignment.getBucketLabel() != null) {
                Bucket bucket = assignments.getBucket(assignment.getExperimentID(), assignment.getBucketLabel());

                response.put("payload", bucket.getPayload() != null ? bucket.getPayload() : null);
            }
        }

        response.put("status", assignment.getStatus());
        response.put("cache", assignment.getStatus().isCacheable());

        if (assignment.getContext() != null) {
            response.put("context", assignment.getContext().toString());
        }

        return response;
    }


    @POST
    @Path("clearMetadataCache")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Clear assignments metadata cache...")
    @Timed
    public Response clearMetadataCache (@HeaderParam(AUTHORIZATION) @ApiParam(value = EXAMPLE_AUTHORIZATION_HEADER, required = true) final String authorizationHeader) {
        UserInfo.Username userName = authorization.getUser(authorizationHeader);
        authorization.checkSuperAdmin(userName);

        boolean result = Boolean.TRUE;
        try {
            assignments.clearMetadataCache();
        } catch (Exception e) {
            LOGGER.error("Exception occurred while clearing assignments metadata cache...", e);
            result = Boolean.FALSE;
        }
        return httpHeader.headers().entity(result).build();
    }

    /**
     * Get the details of assignments metadata cache
     *
     * @return Details of assignments metadata cache - cache entities and size of each entity cache
     */
    @GET
    @Path("metadataCacheDetails")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get assignments metadata cache details...")
    @Timed
    public Response getMetadataCacheDetails(@HeaderParam(AUTHORIZATION) @ApiParam(value = EXAMPLE_AUTHORIZATION_HEADER, required = true) final String authorizationHeader) {
        UserInfo.Username userName = authorization.getUser(authorizationHeader);
        authorization.checkSuperAdmin(userName);

        return httpHeader.headers().entity(assignments.metadataCacheDetails()).build();
    }

}
