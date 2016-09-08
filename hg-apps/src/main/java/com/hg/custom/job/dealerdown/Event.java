package com.hg.custom.job.dealerdown;

import java.time.Instant;

/**
 * Created by scott on 08/09/16.
 */
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
