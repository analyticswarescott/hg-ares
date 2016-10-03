package com.hg.custom.rules;

import com.aw.alarm.SMSManager;
import com.aw.compute.detection.SimpleRule;
import com.aw.document.Document;
import com.aw.document.DocumentMgr;
import com.aw.document.DocumentType;
import com.aw.unity.Data;
import com.aw.unity.Field;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import java.util.Properties;

/**
 * Created by scott on 03/10/16.
 */
public class OpsAlertRule extends SimpleRule {

    public  OpsAlertRule() throws Exception {
        super();
    }

    @Override
    protected void executeActions(Data data) throws Exception {
        //send SMS
        Properties properties = new Properties();
        SMSManager foober = new SMSManager("", properties);

        foober.startup();
        String msg = data.toJsonString(true, true, true);


        //TODO: will need a tenant doc editor, but this will get read every time which is correct for dynamic operation
        Document doc = getDependency(DocumentMgr.class).getDocHandler().getDocument(DocumentType.SMS_SEND_LIST, "critical_ops_event");

        JSONObject recipients = doc.getBody();
        JSONArray numbers = recipients.getJSONArray("recipients");

        for (int i = 0; i< numbers.length(); i++) {
            foober.sendMessage(numbers.getString(i), "Critical Operations Event \n" + formatOpsEvent(data));
        }

        foober.shutdown();

    }


    private String formatOpsEvent(Data data) {
        StringBuffer ret = new StringBuffer();

        ret.append("Site ID: " + data.getValue(data.getType().getField("site_id")) + "\n");
        ret.append("Table ID: " + data.getValue(data.getType().getField("tableId")) + "\n");
        ret.append("Event Type: " + data.getValue(data.getType().getField("opsEventType")) + "\n");
        ret.append("Message: " + data.getValue(data.getType().getField("message")) + "\n");
        ret.append("Timestamp: " + data.getValue(data.getType().getField("eventTimestamp")) + "\n");


        return ret.toString();


    }
}
