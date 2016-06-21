package com.aw.streaming;

import org.codehaus.jettison.json.JSONObject;

import com.aw.BaseIntegrationTest;
import com.aw.platform.Platform;
import com.aw.platform.restcluster.LocalRestMember;

public class StreamingWork {


    public static boolean launchGlobalEvents(Platform platform, LocalRestMember member) throws  Exception {


		int numRetries = 0;
        while (numRetries < 30) {
            if (member.getController().isProcessorRegistered("global_Events")){break;}
			numRetries++;
            Thread.sleep(1000);
        }

        //add bundles to tenant bundle topic
        DataFeedUtils.fireTenantData(platform, "1", "test_event1.json", "events");

        System.out.println(" wait for indexes to populate....  ");
        //wait for ES records to show up -- this should also test ES provisioning since index should exist

        return true;

    }




}
