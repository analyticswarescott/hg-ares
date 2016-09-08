package com.hg.custom.job.dealerdown;

/**
 * Created by scott on 08/09/16.
 */
public class Pair {

    public enum PairType {
        ACTIVE,
        IDLE,
        IGNORE;
    }

    public Event getFirst() {
        return first;
    }

    private Event first;

    public Event getSecond() {
        return second;
    }

    private Event second;


    public long getDuration() {return duration;}
    public void setDuration(long duration) {this.duration = duration;}
    private long duration;


    public PairType getPairType() {
        return pairType;
    }

    private PairType pairType;

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer();

        ret.append("\nFirst Event: " + first.toString() + "\n");
        ret.append("Second Event: " + second.toString() + "\n");
        ret.append("Pair Type: " + pairType + "\n");
        ret.append("Duration: " + duration + "\n");

        return ret.toString();
    }

    public Pair(Event first, Event second) {
        this.first = first;
        this.second = second;
        this.duration = this.second.getTimestamp() - this.first.getTimestamp();
        try {
            evaluate();
        } catch (Exception e) {
            throw new RuntimeException(" error evaluating pair ", e);
        }
    }

    public void evaluate () throws Exception {


        switch (first.getEventType()) {
            case DealerLogon:

                switch (second.getEventType()) {
                    case EndRound:
                        pairType = PairType.ACTIVE;
                        break;

                    case TableIdle:
                        pairType = PairType.IDLE;
                        break;

                    case DealerLogout:
                        pairType = PairType.IDLE;
                        break;

                    case DealerLogon:
                        if (duration == 0) {
                            pairType = PairType.IGNORE;
                        }
                        else {
                            throw new Exception(" bad second event with time elapsed in pair " + this.toString() );
                        }
                        break;
                    default:
                        throw new Exception(" unexpected second event type " + second.getEventType());
                }

                break;



            case EndRound:

                switch (second.getEventType()) {
                    case EndRound:
                        pairType = PairType.ACTIVE;
                        break;
                    case TableIdle:
                        pairType = PairType.IDLE;
                        break;
                    case DealerLogout:
                        pairType = PairType.ACTIVE;
                        break;
                    default:
                        throw new Exception(" unexpected second event type " + second.getEventType());
                }

                break;


            case TableIdle:
                switch (second.getEventType()) {
                    case EndRound:
                        pairType = PairType.IDLE;
                        break;
                    case TableIdle:
                        pairType = PairType.IDLE;
                        break;
                    case DealerLogout:
                        pairType = PairType.IDLE;
                        break;

                    case DealerLogon:
                        if (duration == 0) {
                            pairType = PairType.IGNORE;
                        }
                        else {
                            throw new Exception(" bad second event with time elapsed in pair " + this.toString() );
                        }
                        break;

                    default:
                        throw new Exception(" unexpected second event type " + second.getEventType());
                }
                break;

            case DealerLogout:
                if (duration == 0) {
                    pairType = PairType.IGNORE;
                }
                else {
                    throw new RuntimeException("Dealer Logout should never have a following event with time elapsed ");
                }

            default:
                System.out.println(" bad first event type " + first.getEventType());




        }


    }


}
