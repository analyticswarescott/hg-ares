package com.hg.custom.common;

/**
 * Created by scott on 03/10/16.
 */
public enum OpsEventType {

    StartUp,  					// Component Startup
    Quiescent,					// Component Quiescent
    ShutDown, 					// Component Shutdown
    AuthException, 				// generate in table authenticator
    ChipDBUpdate, 				// update Chip database
    GameDefUpdate,				// update Game definition
    PersonDBUpdate,				// update Personal database
    ChipColorsDBUpdate,			// update ChipColors database
    ChipCounts,					// send chip counts
    TableDBUpdate,				// update Table database
    HeartbeatIntervalUpdate,	// update of Heartbeat interval
    CalibrateTable,				// Table Calibration Performed
    CalibrateCountingKiosk,		// Counting Kiosk Calibration Performed
    CommScheduleOptionChange,	// Change in the Commission Schedule Option in use
    TournamentModeChange,		// Change in Tournament Mode Flag
    SpotPayoutThresholdChange,	// Change in the Spot Payout Threshold
    MUXStatusActive,			// Connected to MUX (Table Manager) in Active mode
    MUXShutdown,				// Shutdown connection to MUX (Table Manager)
    MUXStreamShutdown,			// Shutdown MUX Stream Endpoint
    MUXStreamStartup,			// Startup of MUX Stream Endpoint
    MUXControlTimeout(1),			// ERROR: MUX Timeout to Control Endpoint
    MUXControlError,			// ERROR: MUX Error via Control Endpoint
    MUXStreamError,				// ERROR: MUX Error via Stream Endpoint
    MUXDatagramSeqError,		// ERROR: MUX Datagram received out of sequence
    WALReadSeqError,            // ERROR: Write-ahead-Log Read error
    WALReadSeqResolved,         // ERROR: Write-ahead-Log Read error resolved.
    ShoeServerShutdown,			// Shoe Server Shutdown
    ShoeServerStartup,			// Shoe Server Startup
    ShoeServerError,			// ERROR: Error encountered with Shoe Server
    TCRecycle,					// Recycle Table Controller
    TCManagerStarted,			// TC Table Manager started
    TCManagerShutdown,			// TC Table Manager shutdown
    TCManagerError;				// ERROR: TC Table Manager encountered fatal error



    OpsEventType() {
        this.severity = 4; //default to not severe
    }

     OpsEventType(int severity) {

    }

    public int getSeverity() {
        return severity;
    }

    private int severity;
}
