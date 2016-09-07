package com.hg.custom.job;

import com.aw.common.messaging.Topic;
import com.aw.common.rdbms.DBConfig;
import com.aw.common.rdbms.DBMgr;
import com.aw.common.rest.security.SecurityUtil;
import com.aw.common.task.*;
import com.aw.common.task.TaskStatus.State;
import com.aw.common.task.exceptions.TaskException;
import com.aw.common.task.exceptions.TaskInitializationException;
import com.aw.common.tenant.Tenant;
import com.aw.common.util.JSONUtils;
import com.aw.common.util.TimeSource;
import com.aw.document.jdbc.JDBCProvider;
import com.aw.platform.PlatformMgr;
import com.aw.platform.monitoring.*;
import com.aw.platform.restcluster.PlatformController;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.common.FailedToSendMessageException;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	private static final String LAST_CALC = "last_poll";

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

			Instant lastStatus = service.get(taskDef, LAST_CALC, Instant.class);
			if (lastStatus == null) {
				lastStatus = Instant.MIN;
			}

			if (pollCount > 0) {
				ret.setStatusMessage("last status poll: " + lastStatus);
			}

			else {
				ret.setStatusMessage("no polls yet");
			}

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

		//initialize lastStatus
		lastStatus = service.get(taskDef, LAST_CALC, Instant.class);
		if (lastStatus == null) {
			lastStatus = Instant.ofEpochMilli(0);
		}

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
			LOGGER.warn(" Status poll cancelled due to platform state: " + state + " deferring status polling untill RUNNING state is detected ");
			return;
		}

		LOGGER.error("DEBUG: ---------------------------------Calculating dead spread -- ");
		System.out.println(" =-=-=-=-=-= Last status time was: " + lastStatus);

		//get config
		JSONObject config = taskDef.getConfig();

		JSONObject dbc = config.getJSONObject("db");
		HashMap<String,String> dbConfig =
		new ObjectMapper().readValue(dbc.toString(), HashMap.class);

		//System.out.println(config.toString());

		//calculate timestamp
		long ls = lastStatus.toEpochMilli();
		long milliWatermark  = 0;
		if (ls > 0 ) { //go back 10 minutes
			milliWatermark = lastStatus.minus(1, ChronoUnit.DAYS).toEpochMilli();
		}


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
						 + " LIMIT 10 " //TODO: DEBUG
				 );

				System.out.println(ps.toString());
				ResultSet rs = ps.executeQuery();

				while (rs.next()) {
					String DDID = rs.getString("dealer_down_id");
					String ddStart = rs.getTimestamp("down_start_ts").toString();
					String ddEnd = rs.getTimestamp("down_end_ts").toString();
					String table_id = rs.getString("table_id");
					String siteID = rs.getString("site_id");


					System.out.println("DeadSpreadCalculator:  PROCESSING DDID: " + DDID);
					processDealerDown(siteID, dbConfig, DDID, table_id, ddStart, ddEnd);
				}


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


		//increment the poll count
		pollCount++;

		//mark the poll time
		lastStatus = timeSource.now();

		//update cluster
		service.put(taskDef, LAST_CALC, lastStatus);

	}


	//TODO: threading (limit is probably good enough)
	private void processDealerDown(String siteID, Map<String, String> dbConfig,  String DDID, String locationID,   String ddStart, String ddEnd) throws Exception{


		//TODO: loop rounds in the time slive and compute relevant round totals for DD





		String sql = "select * " +
				" from  hgbi.fact_event_timeline " +
		" where location_id = ? and site_id = ? " +
		" and event_ts between ? and ?" +
		" and event_type in (?,?,?,?) " +
		" order by event_ts";



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
				ps = conn.prepareStatement(sql);

				int paramIndex =1;
				ps.setString(paramIndex++, locationID);
				ps.setString(paramIndex++, siteID);
				ps.setTimestamp(paramIndex++, Timestamp.valueOf(ddStart));
				ps.setTimestamp(paramIndex++,Timestamp.valueOf(ddEnd));

				ps.setString(paramIndex++, EventType.DealerLogon.toString());
				ps.setString(paramIndex++, EventType.DealerLogout.toString());
				ps.setString(paramIndex++, EventType.EndRound.toString());
				ps.setString(paramIndex++, EventType.TableIdle.toString());

				System.out.println("EVENT RETRIEVAL QUERY: " + ps.toString());
				ResultSet rs = ps.executeQuery();


				long activeSeconds = 0;
				long inactiveSeconds = 0;

				int rowIndex = 0;
				Event prevEvent = null;

				//create audit doc header and array for pair results


				while (rs.next()) {
					String eventID = rs.getString("event_id");
					String eventType = rs.getString("event_type");
					Timestamp eventTs = rs.getTimestamp("event_ts");

					Event event = new Event(eventID, EventType.valueOf(eventType), eventTs.toInstant().toEpochMilli());

					if (prevEvent != null) {
						Pair pair = new Pair(prevEvent, event);;

						//LOGGER.error("DEBUG: PAIR result " + pair.toString());

						switch (pair.getPairType()) {
							case ACTIVE:
								activeSeconds+=  pair.duration /1000;
								break;
							case IDLE:
								inactiveSeconds += pair.duration/1000;
								break;
							default:
								throw new Exception(" bad pair type " + pair.getPairType());
						}

					}



					prevEvent = event;
				}

				LOGGER.error("DEBUG: seconds ratio for DD "  + DDID + ": " + inactiveSeconds + "/" + activeSeconds);
				//update row with DS metrics

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

		//compute

		//record pairs and log audit to ES index




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
	public Instant getLastStatus() { return lastStatus; }
	private Instant lastStatus;

	/**
	 * The number of times we've run
	 */
	public long getPollCount() { return pollCount; }
	private long pollCount;

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

	public enum EventType {
		DealerLogon,
		DealerLogout,
		EndRound,
		TableIdle,
		MainLockboxRemoved,
		MainLockboxReplaced,
		JackpotLockboxRemoved,
		JackpotLockboxReplaced;
	}

	class Event {
		public EventType getEventType() {
			return eventType;
		}

		public void setEventType(EventType eventType) {
			this.eventType = eventType;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(long timestamp) {
			this.timestamp = timestamp;
		}

		private EventType eventType;
		private long timestamp;

		public String getEventID() {
			return eventID;
		}

		public void setEventID(String eventID) {
			this.eventID = eventID;
		}

		private String eventID;

		@Override
		public String toString() {
			return eventID + " : " + getEventType() + " : " + Instant.ofEpochMilli(timestamp);
		}


		public Event(String eventID, EventType type, long timestamp) {
			this.eventType = type;
			this.timestamp = timestamp;
			this.eventID = eventID;

		}


	}

	public enum PairType {
		ACTIVE,
		IDLE;
	}



	class Pair {

		private Event first;
		private Event second;


		public long getDuration() {return duration;}
		public void setDuration(long duration) {this.duration = duration;}
		private long duration;


		public PairType getPairType() {
			return pairType;
		}

		private PairType pairType;

		@Override
		public String toString() {
			StringBuffer ret = new StringBuffer();

			ret.append("\nFirst Event: " + first.toString() + "\n");
			ret.append("Second Event: " + second.toString() + "\n");
			ret.append("Pair Type: " + pairType + "\n");
			ret.append("Duration: " + duration + "\n");

			return ret.toString();
		}

		public Pair(Event first, Event second) {
			this.first = first;
			this.second = second;
			this.duration = this.second.getTimestamp() - this.first.timestamp;
			try {
				evaluate();
			} catch (Exception e) {
				throw new RuntimeException(" error evaluating pair ", e);
			}
		}

		public void evaluate () throws Exception {


			switch (first.getEventType()) {
				case DealerLogon:

					switch (second.getEventType()) {
						case EndRound:
							pairType = PairType.ACTIVE;
							break;

						case TableIdle:
							pairType = PairType.IDLE;
							break;

						case DealerLogout:
							pairType = PairType.IDLE;
							break;

						default:
							throw new Exception(" unexpected second event type " + second.eventType);
					}

					break;



				case EndRound:

					switch (second.getEventType()) {
						case EndRound:
							pairType = PairType.ACTIVE;
							break;
						case TableIdle:
							pairType = PairType.IDLE;
							break;
						case DealerLogout:
							pairType = PairType.ACTIVE;
							break;
						default:
							throw new Exception(" unexpected second event type " + second.eventType);
					}

					break;


				case TableIdle:
					switch (second.getEventType()) {
						case EndRound:
							pairType = PairType.IDLE;
							break;
						case TableIdle:
							pairType = PairType.IDLE;
							break;
						case DealerLogout:
							pairType = PairType.IDLE;
							break;
						default:
							throw new Exception(" unexpected second event type " + second.eventType);
					}
					break;

				case DealerLogout:
						throw new RuntimeException("Dealer Logout should never have a following event");

				default:
					System.out.println(" bad first event type " + first.getEventType());




			}


		}


	}



}

