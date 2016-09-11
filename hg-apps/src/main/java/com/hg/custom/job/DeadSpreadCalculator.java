package com.hg.custom.job;

import com.aw.common.rdbms.DBConfig;
import com.aw.common.rdbms.DBMgr;
import com.aw.common.rest.security.SecurityUtil;
import com.aw.common.task.*;
import com.aw.common.task.TaskStatus.State;
import com.aw.common.task.exceptions.TaskException;
import com.aw.common.task.exceptions.TaskInitializationException;
import com.aw.common.util.JSONUtils;
import com.aw.common.util.TimeSource;
import com.aw.document.jdbc.JDBCProvider;
import com.aw.platform.PlatformMgr;
import com.aw.platform.restcluster.PlatformController;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hg.custom.job.dealerdown.DealerDown;
import kafka.common.FailedToSendMessageException;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A task that collects status - it is also its own task source
 *
 *
 *
 */
public class DeadSpreadCalculator extends AbstractTask {

	public static final String TYPE = "platform_status";

	private static final Logger LOGGER = Logger.getLogger(DeadSpreadCalculator.class);

	//task data, last time this task polled
	private static final String HIGH_WATERMARK = "watermark";

	public static final String  AUDIT_TS_TIMEZONE = "America/Los_Angeles";


	@Override
	public void initialize(TaskContext context) throws TaskInitializationException {
		this.platformMgr = context.getPlatformMgr();
		this.timeSource = context.getTimeSource();
		this.service = context.getTaskService();
		this.lock = new ReentrantLock();
		this.interval = this.lock.newCondition();
		this.taskDef = context.getTaskDef();

	}

	@JsonIgnore
	@Override
	public TaskStatus getStatus() throws TaskException {

		try {

			TaskStatus ret = new TaskStatus();
			ret.setState(State.RUNNING);

		/*	Instant zkWatermark = service.get(taskDef, HIGH_WATERMARK, Instant.class);
			if (zkWatermark == null) {
				zkWatermark = Instant.MIN;
			}

			if (pollCount > 0) {
				ret.setStatusMessage("last status poll: " + zkWatermark);
			}

			else {
				ret.setStatusMessage("no polls yet");
			}*/

			//set properties based on our properties
			JSONUtils.updateFromString(JSONUtils.objectToString(this), ret.getProperties());

			return ret;

		} catch (Exception e) {

			throw new TaskException("error getting status for task", e);

		}


	}

	@Override
	public void execute() throws Exception {

		//set up system access for this thread
		SecurityUtil.setThreadSystemAccess();






		//continually poll for status
		do {

			try {

				lock.lock();

				//wait until next poll TODO: make scheduling something supported at task framework level
				try {

					try {
						calculate();
					}
					catch (FailedToSendMessageException fex) {
						//this is very likely to only happen at startup so reduce noise by omitting stack trace
						// -- TODO: base on platform state
						LOGGER.error("Kafka failed send exception: " + fex.getClass().getTypeName() + " : " + fex.getMessage());
					}
					catch (Exception e) {
						LOGGER.error("error calculating DeadSpread", e);
					}

					interval.awaitUntil(Date.from(timeSource.now().plus(pollFrequency)));

					//break if not running
					if (!running) {
						break;
					}

				} finally {
					lock.unlock();
				}

			} catch (Exception e) {
				errorCount++;
				throw e;
			}

		} while (true);

	}

	void calculate() throws Exception {

		PlatformController.PlatformState state = platformMgr.newClient().getPlatformState();
		if (state != PlatformController.PlatformState.RUNNING) {
			LOGGER.warn(" Task cancelled due to platform state: " + state + " deferring status polling untill RUNNING state is detected ");
			return;
		}

		//get any updates to the task def TODO: improve base framework so we don't need this call
		TaskDef fromDB = service.getUpdatedTaskDef(taskDef, taskDef.getTenant().getTenantID());
		//System.out.println(JSONUtils.objectToString(taskDef));
		//System.out.println(JSONUtils.objectToString(fromDB));
		taskDef = fromDB;

		//determine zkWatermark
		zkWatermark = service.get(taskDef, HIGH_WATERMARK, Instant.class);
		if (zkWatermark == null) {
			LOGGER.debug(" last watermark is null ");
			zkWatermark = Instant.ofEpochMilli(0);
		}
		else {
			LOGGER.debug("last watermark is : " + zkWatermark);
		}




		Map<String, String> dbConfig = taskDef.getDBConfig();

		//calculate watermark to use -- TODO: push to a base class
		long milliWatermark =0;
		if (taskDef.getFixedWatermark() != null) {
			milliWatermark = Long.parseLong(taskDef.getFixedWatermark());
			LOGGER.warn("±±±±±±±±±±±±± using fixed watermark =========:  " + Instant.ofEpochMilli(milliWatermark));
		}
		else {
			long ls = zkWatermark.toEpochMilli();
			if (ls > 0 ) { //go back 1 hour from previous high to pick up any lagging events
				milliWatermark = zkWatermark.minus(1, ChronoUnit.HOURS).toEpochMilli();
			}
			LOGGER.warn("==using calculated last-processed timestamp of " + Instant.ofEpochMilli(milliWatermark));
		}
		//set watermark to use from here
		Instant maxTs = Instant.ofEpochMilli(milliWatermark);


		JDBCProvider provider = null;
		Connection conn = null;
		PreparedStatement ps = null;


		try {
			if (provider == null) {
				provider = (JDBCProvider) Class.forName(dbConfig.get(DBConfig.DB_PROVIDER)).newInstance();
			}


			if (conn == null) {
				//get schema-ed connection to the defined target DB
				conn = DBMgr.getConnection(provider.getJDBCURL(dbConfig), dbConfig.get(DBConfig.DB_USER)
						, dbConfig.get(DBConfig.DB_PASS));

				//TODO: add site ID based on tenant
				 ps = conn.prepareStatement(" select * from " + dbConfig.get(DBConfig.DB_SCHEMA) + "."
								 + " fact_dealer_down where audit_insert_ts >= FROM_UNIXTIME(" + milliWatermark + "/1000) "

						 + " and site_id = ? "
						// + " and audit_insert_ts >  '2016-08-03 00:00:00'" //TODO: this excludes pre-end-round time
						// + " and active_minutes is null  " //TODO: DEBUG - to speed the process of seeing all records
				 );

				ps.setString(1, taskDef.getTenant().getTenantID());

				LOGGER.debug( "Dealer down selection SQL: " + ps.toString());
				ResultSet rs = ps.executeQuery();

				int rowsProcessed = 0;
				while (rs.next()) {
					String DDID = rs.getString("dealer_down_id");
					Timestamp ddStart = rs.getTimestamp("down_start_ts");
					Timestamp ddEnd = rs.getTimestamp("down_end_ts");
					String table_id = rs.getString("table_id");
					String siteID = rs.getString("site_id");
					String dealerID = rs.getString("dealer_id");

					Timestamp auditTs = rs.getTimestamp("audit_insert_ts");


					LOGGER.debug("reading DDID: " + DDID
							+ " ended " + mysqlTsToZulu(ddEnd) + " (" + ddEnd + ")");

					DealerDown dd = new DealerDown(DDID,  ddStart, ddEnd, table_id, siteID, dealerID);
					dd.process(dbConfig);

					//get audit ts as Zulu
					Instant currentAuditTsUTC = mysqlTsToZulu(auditTs);

					long diff = currentAuditTsUTC.compareTo(maxTs);
					//if current TS is > max, set max
					if (diff > 0) {
						maxTs = currentAuditTsUTC;
					}

					rowsProcessed++;
				}

				LOGGER.warn(" processed record count: " + rowsProcessed);


			}

		}
		finally {
			if (ps != null) {
				ps.close();
			}
			if (conn != null) {
				conn.close();
			}
		}


		//update cluster
		LOGGER.warn("=================  setting Zookeeper watermark to " + maxTs);
		service.put(taskDef, HIGH_WATERMARK, maxTs);

	}

	private Instant mysqlTsToZulu(Timestamp ts) {
		return ZonedDateTime.of(ts.toLocalDateTime(), ZoneId.of(AUDIT_TS_TIMEZONE)).toInstant();
	}


	@Override
	public void stop() {
		lock.lock();
		try {
			setRunning(false);
		} finally {
			lock.unlock();
		}
	}

	public void shuttingDown() {
		//avoid task execution of poll during shutdown
		lock.lock();
		lock.unlock();
	}

	/**
	 * The last time we successfully ran
	 */
	public Instant getZkWatermark() { return zkWatermark; }
	private Instant zkWatermark;


	/**
	 * The number of times we've failed
	 */
	public long getErrorCount() { return errorCount; }
	private long errorCount;

	/**
	 * @param running whether this poller is running
	 */
	void setRunning(boolean running) { this.running = running; }
	private boolean running = true;

	/**
	 * @return frequency of status poll
	 */
	public Duration getPollFrequency() { return this.pollFrequency; }
	void setPollFrequency(Duration pollFrequency) { this.pollFrequency = pollFrequency; }
	private Duration pollFrequency = Duration.of(30, ChronoUnit.SECONDS);

	private PlatformMgr platformMgr;

	private TimeSource timeSource;

	private TaskService service;

	private ReentrantLock lock;

	private Condition interval;

	private TaskDef taskDef;











}

