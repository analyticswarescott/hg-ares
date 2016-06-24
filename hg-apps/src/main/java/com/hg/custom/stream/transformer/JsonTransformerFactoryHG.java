package com.hg.custom.stream.transformer;

import com.aw.common.spark.JsonTransformer;
import com.aw.common.spark.JsonTransformerFactory;

import java.io.Serializable;

/**
 * return a JSON transformer implementation for a given event type
 */
public class JsonTransformerFactoryHG implements JsonTransformerFactory, Serializable {

	@Override
	public JsonTransformer getTransformer(String eventType) throws Exception {
		//TODO: implement further;
		return new TransformerGameEvent();
	}
}
