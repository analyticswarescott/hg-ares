package com.hg.custom.job.dealerdown;

import com.aw.common.rdbms.DBConfig;
import com.aw.common.rdbms.DBMgr;
import com.aw.document.jdbc.JDBCProvider;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by scott on 08/09/16.
 */
public class DealerDown {

    public static final Logger LOGGER = LoggerFactory.getLogger(DealerDown.class);

    private int pairOrdinal = 0;

    double activeSeconds = 0;
    double inactiveSeconds = 0;

    private String DDID;
    private Timestamp start;
    private Timestamp end;
    String locationID;
    String siteID;
    String dealerID;

    private long duration;

    private TreeMap<Integer, Pair> pairs  = new TreeMap<>();

    public DealerDown(String DDID, Timestamp start, Timestamp end,
                      String locationID, String siteID, String dealerID) {
        this.DDID = DDID;
        this.start = start;
        this.end = end;
        this.locationID = locationID;
        this.siteID = siteID;
        this.dealerID = dealerID;
    }

    public void addPair(Pair pair) {
        pairs.put(pairOrdinal++, pair);
    }


    private String getSummary() {
        double calcDur = activeSeconds + inactiveSeconds;

        if (calcDur == 0) {
            return " Zero Duration";
        }

        DecimalFormat df = new DecimalFormat("##.##%");
        double percent = (activeSeconds / calcDur);
        String formattedPercent = df.format(percent);

        return "active for " + activeSeconds + " / " + calcDur + " ("  + formattedPercent + ")";
    }


    private String formatPairTD(Pair pair) {
        StringBuffer sb = new StringBuffer();
        sb.append("<td>");
        sb.append(pair.getFirst().getEventType());
        sb.append("</td>");
        sb.append("<td>");
        sb.append(Instant.ofEpochMilli(pair.getFirst().getTimestamp()));
        sb.append("</td>");

        sb.append("<td>");
        sb.append("----> ");
        sb.append("</td>");

        sb.append("<td>");
        sb.append(pair.getSecond().getEventType());
        sb.append("</td>");

        sb.append("<td>");
        sb.append(Instant.ofEpochMilli(pair.getSecond().getTimestamp()));
        sb.append("</td>");

        sb.append("<td>");
        sb.append(pair.getPairType());
        sb.append("</td>");

        sb.append("<td>");
        sb.append(pair.getDuration() /1000  + " seconds ");
        sb.append("</td>");

        return sb.toString();
    }

    private String getTitles() {
        StringBuffer sb = new StringBuffer();
        sb.append("<td>");
        sb.append("Event 1");
        sb.append("</td>");

        sb.append("<td>");
        sb.append("Time 1");
        sb.append("</td>");
        sb.append("<td>");
        sb.append("led to");
        sb.append("</td>");
        sb.append("<td>");
        sb.append("Event 2");
        sb.append("</td>");
        sb.append("<td>");
        sb.append("Time 2");
        sb.append("</td>");

        sb.append("<td>");
        sb.append("Type");
        sb.append("</td>");

        sb.append("<td>");
        sb.append("Duration");
        sb.append("</td>");

        return sb.toString();
    }

    public void recordAudit()  throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append("<html>");

        sb.append("<style>");
        sb.append(" td {border-style: solid; border-width: 1px;}");
        sb.append("</style>");

        sb.append("Dealer Down ID: " + DDID + "<br>");
        sb.append("Times: " + start + " to " + end + "<br>");
        sb.append("Site ID: " + siteID + "<br>");
        sb.append("Location/Table ID: " + locationID + "<br>");
        sb.append("Dealer ID: " + dealerID + "<br><br>");

        sb.append("Dead Spread: " + getSummary() + "<br>");


        sb.append("<br>");


        sb.append("<table style='cell-padding: 2px; border-style: solid; ' >");

        //header
        sb.append("<thead  style='display:table-header-group;' >");
        sb.append(getTitles());
        sb.append("</thead>");

        for (Pair p : pairs.values()) {

            sb.append("<tr>");
            sb.append(formatPairTD(p));
            sb.append("</tr>");
        }


        sb.append("</table>");
        sb.append("</html>");

        File a = new File("/tmp/ddaudit.html");
        FileUtils.forceDelete(a);
        FileUtils.writeStringToFile(a, sb.toString());

    }



    public void process( Map<String, String> dbConfig) throws Exception{

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
            }
            //TODO: add site ID based on tenant
            ps = conn.prepareStatement(sql);

            int paramIndex =1;
            ps.setString(paramIndex++, locationID);
            ps.setString(paramIndex++, siteID);
            ps.setTimestamp(paramIndex++, start);
            ps.setTimestamp(paramIndex++, end);

            ps.setString(paramIndex++, EventType.DealerLogon.toString());
            ps.setString(paramIndex++, EventType.DealerLogout.toString());
            ps.setString(paramIndex++, EventType.EndRound.toString());
            ps.setString(paramIndex++, EventType.TableIdle.toString());

            //System.out.println("EVENT RETRIEVAL QUERY: " + ps.toString());
            ResultSet rs = ps.executeQuery();


            int rowIndex = 0;
            Event prevEvent = null;

            //create audit doc header and array for pair results
            activeSeconds = 0;
            inactiveSeconds = 0;

            while (rs.next()) {
                String eventID = rs.getString("event_id");
                String eventType = rs.getString("event_type");
                Timestamp eventTs = rs.getTimestamp("event_ts");

                Event event = new Event(eventID, EventType.valueOf(eventType), eventTs.toInstant().toEpochMilli());

                if (prevEvent != null) {
                    Pair pair = new Pair(prevEvent, event);;
                    addPair(pair);
                    //LOGGER.error("DEBUG: PAIR result " + pair.toString());

                    switch (pair.getPairType()) {
                        case ACTIVE:
                            activeSeconds+=  pair.getDuration() /1000;
                            break;
                        case IDLE:
                            inactiveSeconds += pair.getDuration() /1000;
                            break;
                        case IGNORE:
                            //TODO: note this in audit if required
                            System.out.println(" IGNORED PAIR ===============   "  + pair.toString());
                            break;
                        default:
                            throw new Exception(" bad pair type " + pair.getPairType());
                    }

                }



                prevEvent = event;
            }

            double totalSeconds = inactiveSeconds + activeSeconds;
            LOGGER.error("DEBUG: DEAD TIME ratio for DD [Active]/[Total]"  + DDID + ": " + activeSeconds + "/"
                    + totalSeconds);


            //generate audit information (HTML to a CLOB on the DD?)

            //update row with DS metrics //TODO: re-factor to include round metrics (metric dictionary)
            String updateSql = " update " + dbConfig.get(DBConfig.DB_SCHEMA) + ".fact_dealer_down  " +
                    " set  active_minutes = ? , inactive_minutes = ? " +
                    " where dealer_down_id = ? ";

            paramIndex = 1;
            ps = conn.prepareStatement(updateSql);
            ps.setLong(paramIndex++, (long) activeSeconds);
            ps.setLong(paramIndex++, (long) inactiveSeconds);
            ps.setString(paramIndex++, DDID);

            int rows = ps.executeUpdate();
            if (rows != 1) {//should only happen if DD deleted somehow
                throw new RuntimeException(" rows affected should have been 1, was " + rows + " DDID: " + DDID);
            }

            recordAudit();

        }
        finally {
            if (ps != null) {
                ps.close();
            }
            if (conn != null) {
                conn.close();
            }
        }






    }




}
