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

import com.aw.common.system.EnvironmentSettings;
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
			Exception e = new Exception();
			e.printStackTrace();
			set();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void set() throws Exception{

		//TODO: automate custom testing paths that base test classes can find -- maybe a ETE_TEST_MDOE setting to drive
		System.setProperty("stream_lib_override", "/Users/scott/dev/src/hg-ares/conf/stream");

		System.setProperty("DB_VENDOR", "mysql");

		System.setProperty("ARES_BASE_HOME", "/Users/scott/dev/src/ares/cluster");
		System.out.println("ENV BASE HOME: " + EnvironmentSettings.getAresBaseHome());

		System.setProperty("ARES_HOME", "/Users/scott/dev/src/hg-ares");

		System.out.println("SYSPROP:" + System.getProperty("ARES_HOME"));
		System.out.println("ENV: "  + EnvironmentSettings.getAppLayerHome());

		System.setProperty("ARES_SPARK_HOME", "/Users/scott/dev/src/hg-ares/test/integration/spark_test");
		//System.setProperty("SPARK_LIB_HOME", "/Users/scott/dev/src/ares/cluster/ares-core/compute/target/lib");
	}

	@Test
	public void eventTest() throws Exception {

		//test full file


		testEventToRest();

		testStatusPoll();
		//run an archive now, running as tomorrow
		//testArchive();

	}

/*
	public void testArchive() throws Exception {

		TenantArchiveTask task = new TenantArchiveTask();

		Tenant tenant = Tenant.forId("3");
		Impersonation.impersonateTenant(tenant);

		TaskDef taskDef = TestDependencies.getDocs().get().getDocument(DocumentType.TASK_DEF, "tenant_archive").getBodyAsObject(TaskDef.class);
		assertNotNull(taskDef);

		//pretend it's tomorrow
		TimeSource time = new SetTimeSource(Instant.now().plus(Duration.ofHours(24)));

		task.initialize(new DefaultTaskContext(
				TestDependencies.getTaskContainer().get(),
				time,
				taskDef,
				tenant,
				TestDependencies.getTaskService().get(),
				TestDependencies.getDocMgr().get())
		);

		task.execute();

		//verify we've collected the files
		String filename = FileArchiveProcessor.getFileName(time, task.getArchiveTimeUnit());

		Impersonation.impersonateTenant(tenant);

		FileReader reader = TestDependencies.getPlatformMgr().get().getTenantFileReader();
		assertTrue(reader.exists(HadoopPurpose.ARCHIVE, new Path("/"), filename));

		FileWrapper file = reader.read(HadoopPurpose.ARCHIVE, new Path("/"), filename);
		try (TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(file.getInputStream()))) {

			ArchiveEntry entry = tar.getNextEntry();
			assertEquals("tenant/3/edr/test_machine/test_scan", entry.getName());

		}

	}
*/

	public void testStatus(Tenant tenant, Topic topic, long offset) throws Exception {

		setThreadSystemAccess();
		RestClient client = new RestClient(NodeRole.REST, TestDependencies.getPlatform().get());

		DefaultPlatformStatus status = client.executeReturnObject(HttpMethod.GET,
			VERSIONED_REST_PREFIX + "/platform/status/" + DateTime.now().getMillis(), DefaultPlatformStatus.class, false);

		//assert there are 2 stat reports for localhost
		assertEquals(status.getNodeStatuses().get("localhost").size(), 2);

		//grab the first perf stat object and ensure it has 10 node roles
		OSPerfStats perf = status.getNodeStatuses().get("localhost").get(0).getPerfStats();
		assertEquals(12, perf.getNodeRoleList().size());


		assertEquals(3, status.getTenantStatus().size());
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

		setThreadSystemAccess();

		//for now force a status check on FileWriter because we know Hadoop is up
		TestDependencies.getPlatformMgr().get().getTenantFileWriter().checkStatus(true);
		TestDependencies.getPlatformMgr().get().setPlatform(null);


		provisionTenant("1");

		//fire array of events via REST
		RestClient client = new RestClient(NodeRole.REST, TestDependencies.getPlatform().get());
		HttpResponse resp = client.execute(HttpMethod.PUT, "/rest/1.0/ares/event/1/event", DataFeedUtils.getInputStream("test_game_event.json"));

		System.out.println(" event rest call 1 status: " + resp.getStatusLine().getStatusCode());
		System.out.println(EntityUtils.toString(resp.getEntity()));

		//test UPSERT-tolerance
		HttpResponse resp2 = client.execute(HttpMethod.PUT, "/rest/1.0/ares/event/1/event", DataFeedUtils.getInputStream("test_game_event.json"));

		System.out.println(" event rest call 2 status: " + resp2.getStatusLine().getStatusCode());
		System.out.println(EntityUtils.toString(resp2.getEntity()));


		//send event 2 with a different ID
		HttpResponse resp3 = client.execute(HttpMethod.PUT, "/rest/1.0/ares/event/1/event", DataFeedUtils.getInputStream("test_game_event2.json"));

		System.out.println(" event rest call 3 status: " + resp3.getStatusLine().getStatusCode());
		System.out.println(EntityUtils.toString(resp3.getEntity()));





		//make sure we get the counts we expect
		DataFeedUtils.awaitESResult(ESKnownIndices.EVENTS_ES, Tenant.forId("1") , "GameEvent", 2, 180);

		//count JDBC rows for now -- TODO: assert contents of at least 1 row to check transformations

		//can be timing if ES is too fast
		Thread.sleep(1000);
		assertEquals(" expect 2 game event rows ", 2,
			TestDependencies.getDBMgr().get().executeScalarCountSelect(Tenant.forId("1"),"select count(*) as cnt from gameevent"));

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

		/*//verify zookeeper for tenant 1 TODO: assert full tree once task GUIDs can be resolved
		ZkAccessor zk = new DefaultZkAccessor(TestDependencies.getPlatform().get(), Hive.SYSTEM);
		StringBuffer zkTree = zk.listZK("/aw/tenant/1", true);

		//System.out.println(zkTree.toString());

		Set<String> actual = new HashSet<String>(Arrays.asList(zkTree.toString().split("\\n")));
		Set<String> actualExtra = new HashSet<>(actual);
		Set<String> actualMissing = new HashSet<>(EXPECTED_TENANT_1_ZK_TREE);

		//locks only used to support sharded DB, which is not the default
		 //actualMissing.addAll(EXPECTED_TENANT_1_ZK_TREE_LOCKS);

		//if the database is sequenced, we expect locks in zk
		if (TestDependencies.getJdbcProvider() instanceof SequencedDocumentHandler) {
			actualMissing.addAll(EXPECTED_TENANT_1_ZK_TREE_ZK_DB_SEQ);
		}

		//check  missing
		actualExtra.removeAll(EXPECTED_TENANT_1_ZK_TREE);

		//locks only used to support sharded DB, which is not the default
		//actualExtra.removeAll(EXPECTED_TENANT_1_ZK_TREE_LOCKS);

		//if the database is sequenced, we expect op sequences in zk
		if (TestDependencies.getJdbcProvider() instanceof SequencedDocumentHandler) {
			actualExtra.removeAll(EXPECTED_TENANT_1_ZK_TREE_ZK_DB_SEQ);
		}

		actualMissing.removeAll(actual);

		assertTrue("missing values in zk tree: " + actualMissing + " (extras: " + actualExtra + ")", actualMissing.isEmpty());
		assertTrue("extra values in zk tree: " + actualExtra, actualExtra.isEmpty());*/

	}

	private static Set<String> EXPECTED_TENANT_1_ZK_TREE = new HashSet<String>(Arrays.asList( new String[] {

		"/aw/tenant/1/offsets",
		"/aw/tenant/1/offsets/1_incoming_edr",
		"/aw/tenant/1/offsets/1_incoming_edr/1_scan_ref",
		"/aw/tenant/1/offsets/1_incoming_edr/1_scan_ref/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_incident",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_incident/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_windows_registry",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_windows_registry/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_windows_log",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_windows_log/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_wmi_data",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_wmi_data/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_on_disk_executable",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_on_disk_executable/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_running_process",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_running_process/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_machine_event",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_machine_event/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_action",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_action/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_perf_stat",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_perf_stat/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_errors",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_errors/0",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_topic_status",
		"/aw/tenant/1/offsets/1_detection_simple_rules/1_topic_status/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_incident/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_action/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_incident",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_action",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_perf_stat",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_errors",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_topic_status/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_perf_stat/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_errors/0",
		"/aw/tenant/1/offsets/detection_simple_rules_system/1_topic_status",
		"/aw/tenant/1/offsets/null",
		"/aw/tenant/1/offsets/1_es_load_events",
		"/aw/tenant/1/offsets/1_es_load_events/1_machine_event",
		"/aw/tenant/1/offsets/1_es_load_events/1_machine_event/0",
		"/aw/tenant/1/offsets/1_es_load_edr",
		"/aw/tenant/1/offsets/1_es_load_edr/1_windows_log",
		"/aw/tenant/1/offsets/1_es_load_edr/1_windows_log/0",
		"/aw/tenant/1/offsets/1_es_load_edr/1_wmi_data",
		"/aw/tenant/1/offsets/1_es_load_edr/1_wmi_data/0",
		"/aw/tenant/1/offsets/1_es_load_edr/1_on_disk_executable",
		"/aw/tenant/1/offsets/1_es_load_edr/1_on_disk_executable/0",
		"/aw/tenant/1/offsets/1_es_load_edr/1_running_process",
		"/aw/tenant/1/offsets/1_es_load_edr/1_running_process/0",
		"/aw/tenant/1/offsets/1_es_load_edr/1_windows_registry",
		"/aw/tenant/1/offsets/1_es_load_edr/1_windows_registry/0",
		"/aw/tenant/1/offsets/1_es_load_errors",
		"/aw/tenant/1/offsets/1_es_load_errors/1_errors",
		"/aw/tenant/1/offsets/1_es_load_errors/1_errors/0",
		"/aw/tenant/1/offsets/1_es_load_incidents",
		"/aw/tenant/1/offsets/1_es_load_incidents/1_incident",
		"/aw/tenant/1/offsets/1_es_load_incidents/1_incident/0",
		"/aw/tenant/1/offsets/1_es_load_incidents/1_action",
		"/aw/tenant/1/offsets/1_es_load_incidents/1_action/0",
		"/aw/tenant/1/offsets/1_incoming_bundle",
		"/aw/tenant/1/offsets/1_incoming_bundle/1_bundle_ref",
		"/aw/tenant/1/offsets/1_incoming_bundle/1_bundle_ref/0",
		"/aw/tenant/1/offsets/1_incoming_bundle_kafka",
		"/aw/tenant/1/offsets/1_incoming_bundle_kafka/1_bundle",
		"/aw/tenant/1/offsets/1_incoming_bundle_kafka/1_bundle/0",
		"/aw/tenant/1/offsets/1_es_load_status",
		"/aw/tenant/1/offsets/1_es_load_status/1_topic_status",
		"/aw/tenant/1/offsets/1_es_load_status/1_topic_status/0",
		"/aw/tenant/1/offsets/1_es_load_status/1_perf_stat",
		"/aw/tenant/1/offsets/1_es_load_status/1_perf_stat/0",
		"/aw/tenant/1/offsets/1_es_load_alarms",
		"/aw/tenant/1/offsets/1_es_load_alarms/1_alarm",
		"/aw/tenant/1/offsets/1_es_load_alarms/1_alarm/0",
		"/aw/tenant/1/task",
		"/aw/tenant/1/task/data",
		"/aw/tenant/1/task/running/platform_status",
		"/aw/tenant/1/task/running/platform_status/platform_status_poller",
		"/aw/tenant/1/task/data/platform_status",
		"/aw/tenant/1/task/data/platform_status/platform_status_poller_last_poll",
		"/aw/tenant/1/task/running"
	}));


	private static Set<String> EXPECTED_TENANT_1_ZK_TREE_ZK_DB_SEQ = new HashSet<String>(Arrays.asList( new String[] {

		//entries for sequenced database
		"/aw/tenant/1/op_sequence",
		"/aw/tenant/1/op_sequence/workspace",
		"/aw/tenant/1/op_sequence/simple_rule",
		"/aw/tenant/1/op_sequence/unity_instance",
		"/aw/tenant/1/op_sequence/query",
		"/aw/tenant/1/op_sequence/unity_locale",
		"/aw/tenant/1/op_sequence/filter",
		"/aw/tenant/1/op_sequence/config_index",
		"/aw/tenant/1/op_sequence/user_settings",
		"/aw/tenant/1/op_sequence/hud",
		"/aw/tenant/1/op_sequence/stream_tenant",
		"/aw/tenant/1/op_sequence/investigation",
		"/aw/tenant/1/op_sequence/unity_field_repo",
		"/aw/tenant/1/op_sequence/unity_datatype_repo",

	}));

	private static Set<String> EXPECTED_TENANT_1_ZK_TREE_LOCKS = new HashSet<String>(Arrays.asList( new String[] {

			//locks
			"/aw/tenant/1/locks",
			"/aw/tenant/1/locks/config_index-edr",
			"/aw/tenant/1/locks/config_index-edr/leases",
			"/aw/tenant/1/locks/config_index-edr/locks",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle/leases",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle/locks",
			"/aw/tenant/1/locks/config_index-incidents",
			"/aw/tenant/1/locks/config_index-incidents/leases",
			"/aw/tenant/1/locks/config_index-incidents/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_incident",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_incident/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_incident/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_errors",
			"/aw/tenant/1/locks/stream_tenant-es_load_errors/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_errors/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_status",
			"/aw/tenant/1/locks/stream_tenant-es_load_status/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_status/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_events",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_events/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_events/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_perf_stat",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_perf_stat/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_perf_stat/locks",
			"/aw/tenant/1/locks/investigation-event_detail",
			"/aw/tenant/1/locks/investigation-event_detail/leases",
			"/aw/tenant/1/locks/investigation-event_detail/locks",
			"/aw/tenant/1/locks/filter-example",
			"/aw/tenant/1/locks/filter-example/leases",
			"/aw/tenant/1/locks/filter-example/locks",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle_kafka",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle_kafka/leases",
			"/aw/tenant/1/locks/stream_tenant-incoming_bundle_kafka/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_error",
			"/aw/tenant/1/locks/unity_field_repo-dg_error/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_error/locks",
			"/aw/tenant/1/locks/query-event_detail",
			"/aw/tenant/1/locks/query-event_detail/leases",
			"/aw/tenant/1/locks/query-event_detail/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_common",
			"/aw/tenant/1/locks/unity_field_repo-dg_common/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_common/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_edr",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_edr/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_edr/locks",
			"/aw/tenant/1/locks/config_index-status",
			"/aw/tenant/1/locks/config_index-status/leases",
			"/aw/tenant/1/locks/config_index-status/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_edr",
			"/aw/tenant/1/locks/unity_field_repo-dg_edr/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_edr/locks",
			"/aw/tenant/1/locks/user_settings-default",
			"/aw/tenant/1/locks/user_settings-default/leases",
			"/aw/tenant/1/locks/user_settings-default/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_edr",
			"/aw/tenant/1/locks/stream_tenant-es_load_edr/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_edr/locks",
			"/aw/tenant/1/locks/stream_tenant-incoming_edr",
			"/aw/tenant/1/locks/stream_tenant-incoming_edr/leases",
			"/aw/tenant/1/locks/stream_tenant-incoming_edr/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_events",
			"/aw/tenant/1/locks/unity_field_repo-dg_events/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_events/locks",
			"/aw/tenant/1/locks/hud-machine_events",
			"/aw/tenant/1/locks/hud-machine_events/leases",
			"/aw/tenant/1/locks/hud-machine_events/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_action",
			"/aw/tenant/1/locks/unity_field_repo-dg_action/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_action/locks",
			"/aw/tenant/1/locks/workspace-default",
			"/aw/tenant/1/locks/workspace-default/leases",
			"/aw/tenant/1/locks/workspace-default/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_incidents",
			"/aw/tenant/1/locks/stream_tenant-es_load_incidents/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_incidents/locks",
			"/aw/tenant/1/locks/config_index-errors",
			"/aw/tenant/1/locks/config_index-errors/leases",
			"/aw/tenant/1/locks/config_index-errors/locks",
			"/aw/tenant/1/locks/simple_rule-sample_error_rule",
			"/aw/tenant/1/locks/simple_rule-sample_error_rule/leases",
			"/aw/tenant/1/locks/simple_rule-sample_error_rule/locks",
			"/aw/tenant/1/locks/unity_locale-en_us",
			"/aw/tenant/1/locks/unity_locale-en_us/leases",
			"/aw/tenant/1/locks/unity_locale-en_us/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_perf_stat",
			"/aw/tenant/1/locks/unity_field_repo-dg_perf_stat/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_perf_stat/locks",
			"/aw/tenant/1/locks/config_index-events",
			"/aw/tenant/1/locks/config_index-events/leases",
			"/aw/tenant/1/locks/config_index-events/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_status",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_status/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_status/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_events",
			"/aw/tenant/1/locks/stream_tenant-es_load_events/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_events/locks",
			"/aw/tenant/1/locks/simple_rule-sample_rule",
			"/aw/tenant/1/locks/simple_rule-sample_rule/leases",
			"/aw/tenant/1/locks/simple_rule-sample_rule/locks",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules/leases",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules/locks",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_error",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_error/leases",
			"/aw/tenant/1/locks/unity_datatype_repo-dg_error/locks",
			"/aw/tenant/1/locks/unity_instance-default",
			"/aw/tenant/1/locks/unity_instance-default/leases",
			"/aw/tenant/1/locks/unity_instance-default/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_status",
			"/aw/tenant/1/locks/unity_field_repo-dg_status/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_status/locks",
			"/aw/tenant/1/locks/unity_field_repo-dg_incident",
			"/aw/tenant/1/locks/unity_field_repo-dg_incident/leases",
			"/aw/tenant/1/locks/unity_field_repo-dg_incident/locks",
			"/aw/tenant/1/locks/config_index-alarms",
			"/aw/tenant/1/locks/config_index-alarms/locks",
			"/aw/tenant/1/locks/config_index-alarms/leases",
			"/aw/tenant/1/locks/stream_tenant-es_load_alarms",
			"/aw/tenant/1/locks/stream_tenant-es_load_alarms/locks",
			"/aw/tenant/1/locks/stream_tenant-es_load_alarms/leases",
			"/aw/tenant/1/locks/task_def-tenant_prune",
			"/aw/tenant/1/locks/task_def-tenant_prune/leases",
			"/aw/tenant/1/locks/task_def-tenant_prune/locks",
			"/aw/tenant/1/locks/task_def-platform_status_poller",
			"/aw/tenant/1/locks/task_def-platform_status_poller/leases",
			"/aw/tenant/1/locks/task_def-platform_status_poller/locks",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules_system",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules_system/leases",
			"/aw/tenant/1/locks/stream_tenant-detection_simple_rules_system/locks",
			"/aw/tenant/1/locks/task_def-tenant_archive/locks",
			"/aw/tenant/1/locks/task_def-tenant_archive/leases",
			"/aw/tenant/1/locks/task_def-tenant_archive"


	}));

}
