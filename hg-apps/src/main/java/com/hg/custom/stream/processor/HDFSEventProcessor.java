package com.hg.custom.stream.processor;

import com.aw.common.messaging.Topic;
import com.aw.common.spark.JsonTransformer;
import com.aw.common.spark.JsonTransformerFactory;
import com.aw.common.spark.StreamDef;
import com.aw.common.system.FileInputMetadata;
import com.aw.common.util.JSONUtils;
import com.aw.common.util.ResourceManager;
import com.aw.compute.inject.Dependent;
import com.aw.compute.streams.processor.HDFSFileProcessor;
import com.aw.platform.Platform;
import com.aw.unity.dg.CommonField;
import com.aw.util.Statics;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import java.io.InputStream;
import java.util.List;

/**
 * Created by scott on 15/06/16.
 */
public class HDFSEventProcessor implements HDFSFileProcessor, Dependent {

	protected String json_transformer_factory_class;



	@Override
	public void processFile(FileInputMetadata metadata, InputStream in) throws Exception {

		System.out.println(" HDFSEventProcessor : processing file " + metadata.getFilename());


		String eventGroupString = IOUtils.toString(in, Statics.CHARSET);
		JSONArray rawJsons = new JSONArray(eventGroupString);


		//System.out.println(" HDFSEventProcessor : raw JSON: " + rawJsons.toString());
		Platform platform = getDependency(Platform.class);

		//TODO: this should be DI
		JsonTransformerFactory jtf = ResourceManager.JsonTransformerFactorySingleton
			.getInstance(json_transformer_factory_class);


		JsonTransformer xform = null;
		for (int i = 0; i< rawJsons.length(); i++) {
			JSONObject json = rawJsons.getJSONObject(i);

			if (i == 0) {
				xform = jtf.getTransformer(json.getString(CommonField.EVENT_TYPE_FIELD));
			}

			List<JSONObject> processedJSON = xform.transform(json);

			//TODO: type to topic map to allow flexibility -- for now all events to both ES and JDBC
			for (JSONObject j : processedJSON) {

				Producer<String, String> producer = ResourceManager.KafkaProducerSingleton.getInstance(platform);

				System.out.println(" HDFSEventProcessor : sending to JDBC");
				KeyedMessage<String, String> msg = new KeyedMessage<>(Topic.toTopicString(metadata.getTenantID()
						, Topic.EVENTS_JDBC), metadata.getTenantID()
					, JSONUtils.objectToString(j));
					producer.send(msg);

				System.out.println(" HDFSEventProcessor : sent to JDBC");

				msg = new KeyedMessage<>(Topic.toTopicString(metadata.getTenantID(),
						Topic.EVENTS_ES), metadata.getTenantID(),
					JSONUtils.objectToString(j));
				producer.send(msg);

			}

		}




	}


	@Override
	public void init(StreamDef data)  {
		try {

			//init the transformer factory
		json_transformer_factory_class = data.getConfigData().get("json_transformer_factory");

		} catch (Exception ex) {
			throw new RuntimeException(" error initializing transformer factory" , ex);
		}

	}
}
