package com.hg.custom.stream.transformer;

import com.aw.common.spark.JsonTransformer;
import org.codehaus.jettison.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by scott on 15/06/16.
 */
public class TransformerOpsEvent implements JsonTransformer {


	@Override
	public List<JSONObject> transform(JSONObject input) throws Exception {
		ArrayList<JSONObject> ret = new ArrayList<>();

		//noop


		ret.add(input);

		return ret;
	}






	private static String JSON = "";
}



