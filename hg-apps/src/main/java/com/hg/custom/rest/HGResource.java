package com.hg.custom.rest;

import com.aw.common.disaster.DefaultColdStorageProvider;
import com.aw.common.hadoop.structure.HadoopPurpose;
import com.aw.common.messaging.Topic;
import com.aw.common.rdbms.DBConfig;
import com.aw.common.rest.security.Impersonation;
import com.aw.common.system.EnvironmentSettings;
import com.aw.common.task.TaskDef;
import com.aw.common.tenant.Tenant;
import com.aw.document.Document;
import com.aw.document.DocumentMgr;
import com.aw.document.DocumentType;
import com.aw.platform.PlatformMgr;
import com.aw.platform.restcluster.LocalRestMember;
import com.aw.platform.restcluster.RestCluster;
import com.aw.rest.inject.DGBinder;
import com.aw.tenant.TenantMgr;
import com.aw.unity.dg.CommonField;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hg.custom.job.dealerdown.DealerDown;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
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
	protected  Provider<DocumentMgr> docMgr;

	//public static final String COLD_STORAGE_NAMESPACE_PREFIX = "hgdr.analyticsware.com";
	protected DefaultColdStorageProvider coldStorageProvider;

	static Logger logger = LoggerFactory.getLogger(HGResource.class);

	@Inject
	public HGResource(Provider<PlatformMgr> platformProvider, Provider<LocalRestMember> restMember,
					  Provider<RestCluster> restCluster, TenantMgr tenantMgr,
					  Provider<DocumentMgr> docMgr) {



		this.platformProvider = platformProvider;
		this.restMember = restMember;
		this.restCluster = restCluster;
		this.tenantMgr = tenantMgr;
		this.docMgr = docMgr;


		coldStorageProvider = new DefaultColdStorageProvider();
		coldStorageProvider.init(EnvironmentSettings.fetch(EnvironmentSettings.Setting.COLD_STORE_NAMESPACE_PREFIX));
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


			putEvent(jsonStr, siteId, true); //put event to raw, cold storage, and topics

			//System.out.println("REST:  §§§§§§§§§§§§§§±±±±±±± file  " + fileName + " added to HDFS and ticketed for processing ");
			return Response.status(Response.Status.OK).build();

		} catch (WebApplicationException we) {
			throw we;
		} catch (Exception e) {
			// Cleanup, log, etc
			logger.error("Error Processing  Event Request:" , e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
	}


	private void putEvent(String jsonStr, String siteId, boolean doColdStore ) throws Exception{
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


		if (doColdStore && EnvironmentSettings.fetch(EnvironmentSettings.Setting.COLD_STORE_ENABLED).equals("true")) {
			//write to cold storage -- this is skipped on replay and recovery
			is.reset();
			coldStorageProvider.storeStream(eventType + "-" + fileName, is); //event type is s3 prefix
		}


	}







//audit and job methods
	@GET
	@Path("audit/dealer_down/{id}")
	public String getDealerDownAudit(@PathParam("id") String id) throws  Exception {

		Document doc = docMgr.get().getSysDocHandler().getDocument(DocumentType.TASK_DEF, "dead_spread_calculator");
		TaskDef taskDef = doc.getBodyAsObject(TaskDef.class);

		JSONObject config = taskDef.getConfig();

		JSONObject dbc = config.getJSONObject("db");
		HashMap<String,String> dbConfig = new ObjectMapper().readValue(dbc.toString(), HashMap.class);

		return DealerDown.getAudit(dbConfig, id);

	}

	@PUT
	@Path("job/watermark/{job_name}/{siteId}/{timestamp}")
	public Response setFixedJobWatermark(@PathParam("job_name") String jobName, @PathParam("siteId") String siteID,
									   @PathParam("timestamp") String timestamp) throws Exception {


		Impersonation.impersonateTenant(siteID);
		try {
			Document doc = docMgr.get().getDocHandler().getDocument(DocumentType.TASK_DEF, jobName);
			TaskDef taskDef = doc.getBodyAsObject(TaskDef.class);


			if (timestamp.equals("-1")) {
				logger.warn(" setting fixed watermark of task " + doc.getKey() + " to null ");
				taskDef.setFixedWatermark(null);
			} else {
				logger.warn(" setting fixed watermark of task " + doc.getKey() + "  to  " + timestamp);
				taskDef.setFixedWatermark(timestamp);
			}

			doc.setBodyFromObject(taskDef);
			docMgr.get().getDocHandler().updateDocument(doc);

			return Response.ok("timestamp set ").build();
		}
		finally {
			Impersonation.unImpersonate();
		}

	}





}
