package com.hg.custom.rest;

import com.aw.common.hadoop.structure.HadoopPurpose;
import com.aw.common.messaging.Topic;
import com.aw.common.tenant.Tenant;
import com.aw.platform.PlatformMgr;
import com.aw.platform.restcluster.LocalRestMember;
import com.aw.platform.restcluster.RestCluster;
import com.aw.rest.inject.DGBinder;
import com.aw.tenant.TenantMgr;
import com.aw.unity.dg.CommonField;
import io.swagger.annotations.Api;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Resource for custom apps leveraging ARES
 */
@Singleton
@Path(com.aw.util.Statics.REST_VERSION + "/hg")
public class HGResource   {

	protected  Provider<PlatformMgr> platformProvider;
	protected  Provider<LocalRestMember> restMember;
	protected  Provider<RestCluster> restCluster;
	protected  TenantMgr tenantMgr;

	static Logger logger = LoggerFactory.getLogger(HGResource.class);

	@Inject
	public HGResource(Provider<PlatformMgr> platformProvider, Provider<LocalRestMember> restMember,
					  Provider<RestCluster> restCluster, TenantMgr tenantMgr) {


		//TODO: re-factor to allow custom resources to sit atop ARES

		this.platformProvider = platformProvider;
		this.restMember = restMember;
		this.restCluster = restCluster;
		this.tenantMgr = tenantMgr;

		//logger.error("§§§§§§§§§§§§§§§§§§§§§§§§§§§§§ HG RESOURCE INIT §§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§§");

	}


	private void init()
	{

	}

/*	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)*/
	@PUT
	@Path("/event/{siteId}/event")
	public Response putEvent( @PathParam("siteId") String siteId,
							  @Context HttpHeaders headers,
							  String jsonStr) throws WebApplicationException {
		try {

			//site is tenant

			JSONArray rawJsons = new JSONArray(jsonStr);
			JSONObject o = rawJsons.getJSONObject(0);
			String eventType = o.getString(CommonField.EVENT_TYPE_FIELD);
			String str = rawJsons.toString();
			InputStream is = new ByteArrayInputStream(str.getBytes());

			String fileID =  UUID.randomUUID().toString(); //to differentiate in case 2 files processed at same milli
			String fileName = "received_" + Instant.now().toEpochMilli()+ "_" + fileID;

			platformProvider.get().addFile(HadoopPurpose.EVENTS, Topic.EVENT_GROUP, Tenant.forId(siteId), eventType, fileName
					, fileID
					, is
			);

			System.out.println("REST:  §§§§§§§§§§§§§§±±±±±±± file  " + fileName + " added to HDFS and ticketed for processing ");


			return Response.status(Response.Status.OK).build();

		} catch (WebApplicationException we) {
			throw we;
		} catch (Exception e) {
			// Cleanup, log, etc
			logger.error("Error Processing  Event Request:" , e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
	}








}
