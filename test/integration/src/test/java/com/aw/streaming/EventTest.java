package com.aw.streaming;

import static com.aw.util.Statics.VERSIONED_REST_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.aw.common.disaster.DefaultColdStorageProvider;
import com.aw.common.rest.security.Impersonation;
import com.aw.common.spark.StreamDef;
import com.aw.common.system.EnvironmentSettings;
import com.aw.common.util.JSONUtils;
import com.aw.common.util.RestResponse;
import com.aw.document.Document;
import com.sun.tools.doclint.Env;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.aw.TestDependencies;
import com.aw.common.messaging.Topic;
import com.aw.common.rest.security.TenantAware;
import com.aw.common.task.PlatformStatusPoller;
import com.aw.common.task.TaskDef;
import com.aw.common.task.TaskStatus;
import com.aw.common.tenant.Tenant;
import com.aw.common.util.HttpMethod;
import com.aw.common.util.RestClient;
import com.aw.common.util.es.ESKnownIndices;
import com.aw.document.DocumentType;
import com.aw.platform.NodeRole;
import com.aw.platform.monitoring.DefaultPlatformStatus;
import com.aw.platform.monitoring.StreamStatus;
import com.aw.platform.monitoring.SystemTenantStatus;
import com.aw.platform.monitoring.TenantStatus;
import com.aw.platform.monitoring.os.OSPerfStats;


public class EventTest extends StreamingIntegrationTest implements TenantAware {


	@Override
	public void setExtraSysProps() {
		try {
			set();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void set() throws Exception{

		//TODO: automate custom testing paths that base test classes can find -- maybe a ETE_TEST_MDOE setting to drive

		//TODO: Jetty only reads sysprops -- need to refine
		System.setProperty("ARES_BASE_HOME", EnvironmentSettings.getAresBaseHome());
		System.setProperty("ARES_HOME", EnvironmentSettings.getAppLayerHome());

	}

	@Test
	public void eventTest() throws Exception {

		setThreadSystemAccess();

		//for now force a status check on FileWriter because we know Hadoop is up
		TestDependencies.getPlatformMgr().get().getTenantFileWriter().checkStatus(true);
		TestDependencies.getPlatformMgr().get().setPlatform(null);

		provisionTenant("20");


		testGameEvents();

		testStatus(Tenant.forId("20"), Topic.EVENT_GROUP, 3);

		testOpsEvents();

		System.out.println("========== Test passed == pausing to allow manual verification of results"); //TODO: do scalar count of external DB
		Thread.sleep(30000);


	}



	private void testOpsEvents () throws Exception {

		//fire array of ops events via REST
		RestClient client = new RestClient(NodeRole.REST, TestDependencies.getPlatformMgr().get());
		RestResponse resp = client.execute(HttpMethod.PUT, "/rest/1.0/hg/event/20/event", DataFeedUtils.getInputStream("test_ops_events.json"));


		System.out.println(" event rest call 1 status: " + resp.getStatusCode());
		System.out.println(resp.payloadToString());


		//make sure we get the counts we expect
		DataFeedUtils.awaitESResult(ESKnownIndices.EVENTS_ES, Tenant.forId("20"), "OpsEvent", 23, 300);

		System.out.println("========== ES passed == pausing to allow completion of JDBC target insert"); //TODO: do scalar count of external DB
		Thread.sleep(3000);


		//TODO: better way to get target DB for JDBC verification
		Document events_to_jdbc  = TestDependencies.getDocs().get().getDocument(DocumentType.STREAM_TENANT, "events_jdbc");
		StreamDef sd = events_to_jdbc.getBodyAsObject(StreamDef.class);

		Map<String, String> db = (Map<String, String>) sd.getConfigData().get("target_db");

		//System.out.println("DB Config for JDBC events : " + db.toString());
		assertEquals(" expect 32 ops event rows ", 23,
				TestDependencies.getDBMgr().get().executeScalarCountSelect(db, "select count(*) as cnt from opsevent"));





	}


	private void testGameEvents () throws Exception {
		DefaultColdStorageProvider coldStorageProvider = null;
		String namespacePrefix = EnvironmentSettings.fetch(EnvironmentSettings.Setting.COLD_STORE_NAMESPACE_PREFIX);
		if (EnvironmentSettings.fetch(EnvironmentSettings.Setting.COLD_STORE_ENABLED).equals("true")) {
			//clear any test cold storage
			coldStorageProvider = new DefaultColdStorageProvider();
			coldStorageProvider.init(namespacePrefix);


			//TODO: provider should filter on prefix

			for (String namespace : coldStorageProvider.listNamespaces()) {
				if (namespace.startsWith(namespacePrefix)) {
					coldStorageProvider.deleteNamespace(namespace);
					System.out.println(" §§§§--------------------------------->>>>>>>>>>>> COLD STORAGE NAMESPACE CLEARED : " + namespace);
				}
			}

		}


		testEventToRest();

		testStatusPoll();
		//run an archive now, running as tomorrow
		//testArchive();

		Impersonation.impersonateTenant("20");
		try {
			if (EnvironmentSettings.fetch(EnvironmentSettings.Setting.COLD_STORE_ENABLED).equals("true")) {
				for (String namespace : coldStorageProvider.listNamespaces()) {

					if (namespace.startsWith(namespacePrefix)) {
						System.out.println(" checking counts for cold store namespace ======> " + namespace);
						//TODO: check type counts once we have 2 types

						assertEquals("expect 3 files in cold storage ", 3, coldStorageProvider.getKeyList().size());

						System.out.println("  counts for cold store namespace ======> " + namespace +  " : VERIFIED ");
					}

				}
			}
		}
		finally {
			Impersonation.unImpersonate();
		}


		/*while (true) {
			System.out.println(" ===== running perpetually ======= ");
			Thread.sleep(5000);
		}*/
	}


	private void testStatus(Tenant tenant, Topic topic, long offset) throws Exception {

		setThreadSystemAccess();
		RestClient client = new RestClient(NodeRole.REST, TestDependencies.getPlatformMgr().get());

		DefaultPlatformStatus status = client.executeReturnObject(HttpMethod.GET,
			VERSIONED_REST_PREFIX + "/platform/status/" + DateTime.now().getMillis(), DefaultPlatformStatus.class, false);

		//assert there are 2 stat reports for localhost
		assertEquals(status.getNodeStatuses().get("localhost").size(), 2);

		//grab the first perf stat object and ensure it has 10 node roles
		OSPerfStats perf = status.getNodeStatuses().get("localhost").get(0).getPerfStats();
		assertEquals(12, perf.getNodeRoleList().size());


		assertEquals(2, status.getTenantStatus().size());
		Iterator<TenantStatus> i = status.getTenantStatus().iterator();

		while (i.hasNext()) {
			TenantStatus ts = i.next();

			if (ts.getTenant().getTenantID().equals(Tenant.SYSTEM_TENANT_ID)) {
				SystemTenantStatus sts = (SystemTenantStatus) ts;
				assertEquals(sts.getGlobalStreamStatus().size(), 1);
			}

			if (ts.getTenant().getTenantID().equals("1")) {
				assertEquals(10, ts.getStreamStatus().size());
				//TODO: need to add map-type accessibility to assert offsets etc.
			}
		}

		for (TenantStatus tenantStatus : status.getTenantStatus()) {

			//check property for our tenant
			if (tenantStatus.getTenant().equals(tenant)) {
				for (StreamStatus streamStatus : tenantStatus.getStreamStatus()) {
					if (streamStatus.getTopicStatus().containsKey(topic)) {
						assertEquals(offset, streamStatus.getTopicStatus().get(topic).get(0).getLatestProcessed().getPosition());
					}
				}
			}

		}

		assertTrue(status.getRoleStatus().containsKey(NodeRole.KAFKA));
		assertTrue(status.getRoleStatus().containsKey(NodeRole.ELASTICSEARCH));
		assertTrue(status.getRoleStatus().containsKey(NodeRole.HDFS_DATA));
		assertTrue(status.getRoleStatus().containsKey(NodeRole.HDFS_NAME));
		assertTrue(status.getRoleStatus().containsKey(NodeRole.SPARK_MASTER));
		assertTrue(status.getRoleStatus().containsKey(NodeRole.SPARK_WORKER));





	}


	/**
	 * Test processing a full scan zip
	 */

	public void testEventToRest() throws Exception {



		Impersonation.impersonateTenant("20");
		Document doc = TestDependencies.getDocs().get().getDocument(DocumentType.TASK_DEF, "dead_spread_calculator");

		TaskDef dsc = doc.getBodyAsObject(TaskDef.class);

		System.out.println(JSONUtils.objectToString(doc));


		//fire array of events via REST
		RestClient client = new RestClient(NodeRole.REST, TestDependencies.getPlatformMgr().get());
		RestResponse resp = client.execute(HttpMethod.PUT, "/rest/1.0/hg/event/20/event", DataFeedUtils.getInputStream("test_game_event.json"));


		System.out.println(" event rest call 1 status: " + resp.getStatusCode());
		System.out.println(resp.payloadToString());

		//test UPSERT-tolerance
		RestResponse resp2 = client.execute(HttpMethod.PUT, "/rest/1.0/hg/event/20/event", DataFeedUtils.getInputStream("test_game_event.json"));

		System.out.println(" event rest call 2 status: " + resp2.payloadToString());

		//send event 2 with a different ID
		RestResponse resp3 = client.execute(HttpMethod.PUT, "/rest/1.0/hg/event/20/event", DataFeedUtils.getInputStream("test_game_event2.json"));

		System.out.println(" event rest call 3 status: " + resp3.payloadToString());

		//make sure we get the counts we expect
		DataFeedUtils.awaitESResult(ESKnownIndices.EVENTS_ES, Tenant.forId("20"), "GameEvent", 2, 300);

		//count JDBC rows for now -- TODO: assert contents of at least 1 row to check transformations

		//can be timing if ES is too fast
		System.out.println("========== ES passed == pausing to allow completion of JDBC target insert"); //TODO: do scalar count of external DB
		Thread.sleep(3000);

		Document events_to_jdbc  = TestDependencies.getDocs().get().getDocument(DocumentType.STREAM_TENANT, "events_jdbc");
		StreamDef sd = events_to_jdbc.getBodyAsObject(StreamDef.class);

		Map<String, String> db = (Map<String, String>) sd.getConfigData().get("target_db");
		//System.out.println("DB Config for JDBC events : " + db.toString());

		assertEquals(" expect 2 game event rows ", 2,
				TestDependencies.getDBMgr().get().executeScalarCountSelect(db, "select count(*) as cnt from gameevent"));

		Impersonation.unImpersonate();
	/*	System.out.println("========== TEST PASSED!! pause 10 minutes to check system state...comment once test is working");
		Thread.sleep(600000);*/

	}


	private void testStatusPoll() throws Exception {

		System.out.println(" == Testing status poll ");
		Map<TaskDef, TaskStatus> status = TestDependencies.getTaskService().get().getTaskStatus();

		//make sure we have the right number of tasks
		assertEquals(2, status.size());

		TaskDef taskDef = TestDependencies.getDocs().get().getDocument(DocumentType.TASK_DEF, "platform_status_poller").getBodyAsObject(TaskDef.class);

		//make sure it's the platform status task
		TaskStatus taskStatus = status.get(taskDef);
		assertEquals(PlatformStatusPoller.TYPE, taskStatus.getTaskDef().getTaskTypeName());

		DataFeedUtils.awaitESResult(ESKnownIndices.STATUS, Tenant.forId("0"), "topic_status", DataFeedUtils.AT_LEAST_1, 300);
		DataFeedUtils.awaitESResult(ESKnownIndices.STATUS, Tenant.forId("0"), "perf_stat", DataFeedUtils.AT_LEAST_1, 30);



	}



}
