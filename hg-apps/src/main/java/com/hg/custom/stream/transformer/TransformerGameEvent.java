package com.hg.custom.stream.transformer;

import com.aw.common.spark.JsonTransformer;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by scott on 15/06/16.
 */
public class TransformerGameEvent implements JsonTransformer {


	@Override
	public List<JSONObject> transform(JSONObject input) throws Exception {
		ArrayList<JSONObject> ret = new ArrayList<>();

		//add a double value as a test
		input.put("someDouble", 3.1416);
		input.put("someBigInt", 777777777L);
		input.put("someTimestamp", 1466077325363L);

		ret.add(input);

		return ret;
	}






	private static String JSON = "";
}



