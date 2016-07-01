
SET sql_mode = 'ALLOW_INVALID_DATES';

CREATE TABLE GameEvent (
	eventid varchar(200),
	alarmActive int,
	alarmCode int,
	alarmCodeDesc varchar(200),
	someDouble DECIMAL,
	someBigInt DECIMAL,
	someTimestamp TIMESTAMP,
	defaulted_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (eventid)

);

CREATE TABLE alarmcodedescriptions (
  ref_key varchar(200),
  ref_value varchar (200)
);

insert into alarmcodedescriptions values ('12','foober');



CREATE TABLE fact_round_summary_test
(
  round_id VARCHAR(100) UNIQUE PRIMARY KEY NOT NULL,
  event_id VARCHAR(50),
  site_id VARCHAR(50),
  table_id VARCHAR(500),
  game_type VARCHAR(50),
  card_key_serial_number VARCHAR(500),
  round_start_ts TIMESTAMP NOT NULL DEFAULT '0000-00-00 00:00:00' ,
  round_end_ts TIMESTAMP DEFAULT '0000-00-00 00:00:00' NOT NULL,

  anomaly_detected BOOLEAN,
  error_detected BOOLEAN,

  commission_calc BIGINT,
  commission_collected BIGINT,
  commission_total_action BIGINT,
  commission_schedule VARCHAR(500),

  total_action BIGINT,
  total_collected BIGINT,
  total_payout BIGINT,
  total_push BIGINT,

  number_of_seats INTEGER,
  number_of_spots INTEGER,


  /* bac only */
  was_free_hand BOOLEAN,
  was_tournament_mode BOOLEAN,
  was_dragon7 BOOLEAN,
  was_panda8 BOOLEAN,
  was_natural BOOLEAN,
  winner VARCHAR(50),
  winner_hand_value INTEGER,


  event_ts TIMESTAMP DEFAULT '0000-00-00 00:00:00' NOT NULL,
  site_ts TIMESTAMP DEFAULT '0000-00-00 00:00:00' NOT NULL,
  audit_insert_ts TIMESTAMP DEFAULT '0000-00-00 00:00:00' NOT NULL,
  audit_update_ts TIMESTAMP DEFAULT '0000-00-00 00:00:00' NOT NULL
  ,site_name varchar(50)
  ,table_name varchar(50)
  ,dealer_id varchar(50)
);





