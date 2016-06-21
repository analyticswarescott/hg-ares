package com.aw.streaming;

import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aw.TestDependencies;
import com.aw.common.tenant.Tenant;
import com.aw.common.util.es.ESKnownIndices;
import com.aw.platform.NodeRole;
import com.aw.platform.Platform;
import com.aw.platform.roles.Kafka;
import com.aw.unity.es.UnityESClient;
import com.aw.utils.kafka.KafkaImporter;

/**
 * TODO: integrate with common kafka utilities
 */
public class DataFeedUtils {
  public static final Logger logger = LoggerFactory.getLogger(DataFeedUtils.class);

  public static final int AT_LEAST_1 = -1;

  	static File RESOURCES = new File("."+File.separator+"src"+File.separator+"test"+File.separator+"resources"+File.separator+
            DataFeedUtils.class.getPackage().getName().replace('.', File.separatorChar));



    public static void main(String[] args) throws Exception{

    }

    public static String readFile(String filename) throws Exception {

    	//build the path
    	File path = new File(RESOURCES, filename);

    	//return the file
    	return FileUtils.readFileToString(path);

    }

    public static InputStream getInputStream(String filename) throws Exception {
    	File path = new File(System.getProperty("es.path.home") + "/../" + RESOURCES, filename);
    	return new BufferedInputStream(new FileInputStream(path));
    }

    public static InputStream getInputStreamPart(String filename, int index, int parts) throws Exception {

    	//build the path
    	File path = new File(RESOURCES, filename);

    	byte[] data = FileUtils.readFileToByteArray(path);

    	//determine our from/to
    	int chunkSize = data.length / parts;
    	int from = index * chunkSize;
    	int to = (index + 1) * chunkSize;

    	//make sure we get the whole thing on the last chunk
    	if (index == parts-1) {
    		to = data.length;
    	}

    	//build the array for this part
    	data = Arrays.copyOfRange(data, from, to);

    	//return the input stream for this part
    	return new ByteArrayInputStream(data);

    }



	public static void fireTenantData(Platform platform, String tid, String eventFileName, String topic) throws Exception {
		System.out.println("firing bundles ");

		String s =  readFile(eventFileName);
		JSONObject rawData = new JSONObject(s);
		JSONObject[] msgs = new JSONObject[1];

		msgs[0] = rawData;//TODO: support array

		seedTopic(Tenant.forId(tid),platform, tid + "_" + topic, msgs);


	}



    public static void seedTopic(Tenant tenant, Platform platform, String sourceTopicName, JSONObject[] messageObj) throws Exception {

                        KafkaImporter ki = new KafkaImporter(null, platform.getNode(NodeRole.KAFKA).getHost() +
                               ":" + platform.getNode(NodeRole.KAFKA).getSettingInt(Kafka.PORT)  , "");


                         for (int i=0; i<messageObj.length; i++) {
                             JSONObject msg = messageObj[i];
                             System.out.println("sending kafka message");
                             ki.sendMessage(sourceTopicName, tenant.getTenantID(), msg.toString());
                         }


    }

    public static void awaitESResult(ESKnownIndices index, Tenant tenant, String type, long count, long timeoutSecs) throws Exception{
    	String strIndex = index.buildIndexFor(tenant, Instant.now());
        long startTime = System.currentTimeMillis();
        long maxWait = timeoutSecs * 1000L; //after this much time we fail
        long last = 0L;
        while (true) {
            UnityESClient client = new UnityESClient(TestDependencies.getPlatform().get());
            long esCount = client.docCount(strIndex, type);

            if (esCount > 0) {
            	//keep the dots on the line above us
            	if (last == 0) {
            		System.out.println();
            	}

            	System.out.print("percent complete: " + NumberFormat.getPercentInstance().format((double) esCount / (double) count) + " count: " + esCount + "\r");

            	//< 0 means just wait for something
            	if (count < 0) {
            		break;
            	}

            }
            else {System.out.print(".");}

            assertTrue("document count for " + type + " greater than expected, actual=" + esCount + " expected=" + count, count < 0 || esCount <= count);

            if (esCount == count) {
            	System.out.println();
                break;
            }

            //don't wait forever
            assertTrue("maximum wait time exceeded for elasticsearch data in " + index + "/" + type, (System.currentTimeMillis() - startTime) < maxWait); //after this max wait we fail

            Thread.sleep(1000);

            last = esCount;

        }
    }


    public static boolean awaitESResultBoolean(String index, String type, long count, long timeoutSecs) throws Exception{
        long startTime = System.currentTimeMillis();
        long maxWait = timeoutSecs * 1000L; //after this much time we fail
        while (true) {
            UnityESClient client = new UnityESClient(TestDependencies.getPlatform().get());
            long esCount = client.docCount(index, type);

            if (esCount > 0) {
                System.out.print("percent complete: " + NumberFormat.getPercentInstance().format((double)esCount / (double)count) + " count: " + esCount + "\r");
            }

            if (esCount == count) {
                System.out.println();
                break;
            }

			//wait a little between checks due to type not existing

            //don't wait forever
            if ((System.currentTimeMillis() - startTime) > maxWait) {
				logger.error(" max wait exceeded for " + index + "/" + type );
                return false;
            }


        }

        return true;
    }





}
