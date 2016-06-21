package com.aw.streaming;

import com.aw.BaseIntegrationTest;
import com.aw.TestDependencies;

/**
 *
 * @author jlehmann
 *
 */
public class StreamingIntegrationTest extends BaseIntegrationTest {


	@Override
	protected boolean usesSpark() {
		return true;
	}

	@Override
	protected boolean startsSpark() {
		return true;
	}

    @Override
	public void setExtraSysProps() {

	}
/*	protected void doTest() throws Exception {

        //in this area we do things that require wrappers
        //re-usable work can be done in StreamingWork static so it can be shared among cluster/failure tests

        StreamingWork.fireDLPWork(TestDependencies.getPlatform().get(), TestDependencies.getRestMember().get());


        //TODO: offset and upsert testing -- deferred until we can get sprint_4 branch up with installer


    }*/

}
