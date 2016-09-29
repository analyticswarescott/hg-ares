
SET sql_mode = 'ALLOW_INVALID_DATES';

DROP TABLE IF EXISTS GameEvent;

CREATE TABLE GameEvent (
  eventid varchar(200),
  alarmActive int,
  alarmCode int,
  alarmCodeDesc varchar(200),
  someDouble DECIMAL,
  someBigInt DECIMAL,
  someTimestamp TIMESTAMP,
  defaulted_ts TIMESTAMP,
  PRIMARY KEY (eventid)

);

DROP TABLE IF EXISTS OpsEvent;
CREATE TABLE OpsEvent (
  eventId varchar(200),
  opsEventType varchar(200),
  message varchar(200),
  tableID varchar(200),
  site_id varchar(200),
  siteDesc varchar(200),
  site_ts TIMESTAMP,
  eventTimestamp TIMESTAMP,
  PRIMARY KEY (eventId)

);



DROP TABLE IF EXISTS alarmcodedescriptions;
CREATE TABLE alarmcodedescriptions (
  ref_key varchar(200),
  ref_value varchar (200)
);

insert into alarmcodedescriptions values ('12','foober');

DROP TABLE IF EXISTS sitecodedescriptions;
CREATE TABLE sitecodedescriptions (
  ref_key varchar(200),
  ref_value varchar (200)
);

insert into sitecodedescriptions values ('20','Cordova');




DROP TABLE IF EXISTS fact_dealer_down;
CREATE TABLE `fact_dealer_down` (
  `dealer_down_id` varchar(50) NOT NULL,
  `site_id` varchar(50) DEFAULT NULL,
  `table_id` varchar(500) DEFAULT NULL,
  `game_type` varchar(50) DEFAULT NULL,
  `dealer_id` varchar(50) DEFAULT NULL,
  `game_day` date DEFAULT NULL,
  `calendar_day` date DEFAULT NULL,
  `card_key_serial_number` varchar(500) DEFAULT NULL,
  `down_start_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `down_end_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `number_of_rounds` int(11) DEFAULT NULL,
  `active_minutes` int(11) DEFAULT NULL,
  `inactive_minutes` int(11) DEFAULT NULL,
  `total_fees` int(11) DEFAULT NULL,
  `total_action` int(11) DEFAULT NULL,
  `total_payout` int(11) DEFAULT NULL,
  `total_collected` int(11) DEFAULT NULL,
  `total_push` int(11) DEFAULT NULL,
  `total_number_of_bets` int(11) DEFAULT NULL,
  `high_wager` int(11) DEFAULT NULL,
  `low_wager` int(11) DEFAULT NULL,
  `number_of_payout_alarms` int(11) DEFAULT NULL,
  `number_of_dealer_alarms` int(11) DEFAULT NULL,
  `audit_insert_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `audit_update_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`dealer_down_id`),
  UNIQUE KEY `dealer_down_id` (`dealer_down_id`),
  KEY `fact_dealer_down_idx_site_id` (`site_id`),
  KEY `fact_dealer_down_idx_table_id` (`table_id`(333)),
  KEY `idx_fact_dealer_down_down_start_ts` (`down_start_ts`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;


INSERT INTO `fact_dealer_down` (`dealer_down_id`, `site_id`, `table_id`, `game_type`, `dealer_id`, `game_day`, `calendar_day`, `card_key_serial_number`, `down_start_ts`, `down_end_ts`, `number_of_rounds`, `active_minutes`, `inactive_minutes`, `total_fees`, `total_action`, `total_payout`, `total_collected`, `total_push`, `total_number_of_bets`, `high_wager`, `low_wager`, `number_of_payout_alarms`, `number_of_dealer_alarms`, `audit_insert_ts`, `audit_update_ts`)
VALUES
  ('d481f6ec-6a1d-4519-ba79-9dd417b737f2', '20', '009', NULL, 'stesala', '2016-08-03', '2016-08-03', '0000000000000000E02B003000034B1C', '2016-08-03 12:36:21', '2016-08-03 13:17:31', NULL, 2470, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2016-08-03 13:17:51', '2016-08-03 13:17:51');



DROP TABLE IF EXISTS fact_event_timeline;
CREATE TABLE `fact_event_timeline` (
  `event_id` varchar(50) NOT NULL,
  `site_id` varchar(50) DEFAULT NULL,
  `event_type` varchar(50) DEFAULT NULL,
  `event_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `location_id` varchar(50) DEFAULT NULL,
  `object_id` varchar(50) DEFAULT NULL,
  `person_id` varchar(50) DEFAULT NULL,
  `session_id` varchar(50) DEFAULT NULL,
  `audit_insert_ts` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `event_id` (`event_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;


INSERT INTO `fact_event_timeline` (`event_id`, `site_id`, `event_type`, `event_ts`, `location_id`, `object_id`, `person_id`, `session_id`, `audit_insert_ts`)
VALUES
  ('e202dca4-e316-4ba8-b587-196daf82e3ad', '20', 'DealerLogon', '2016-08-03 12:36:21', '009', '009', 'stesala', 'd481f6ec-6a1d-4519-ba79-9dd417b737f2', '2016-08-03 12:36:51'),
  ('d7aebe8d-dc9e-4566-828f-e8394cc6b4a6', '20', 'EndRound', '2016-08-03 12:37:29', '009', NULL, '0000000000000000E02B003000034B1C', 'fd560423-923f-4fff-b888-5f741492e1d2', '2016-08-03 12:37:52'),
  ('951f0952-6bc1-4c7b-a36b-6f7b44d784a1', '20', 'EndRound', '2016-08-03 12:40:22', '009', NULL, '0000000000000000E02B003000034B1C', 'b0d2be3e-d941-4e61-a084-0e6cb32fd4bc', '2016-08-03 12:40:51'),
  ('a9fd5041-9e31-495d-9a76-152d61c56ab1', '20', 'EndRound', '2016-08-03 12:41:38', '009', NULL, '0000000000000000E02B003000034B1C', 'fa4f068c-3e9b-4078-a799-4b21b34d9d47', '2016-08-03 12:41:58'),
  ('8fe0e246-985f-4e68-910c-74607de2272e', '20', 'EndRound', '2016-08-03 12:42:57', '009', NULL, '0000000000000000E02B003000034B1C', '30a35056-e27c-49d2-b15a-5beb2de9e9fd', '2016-08-03 12:43:21'),
  ('b3292bc9-8326-42e7-934e-e5d27af66b9e', '20', 'EndRound', '2016-08-03 12:44:15', '009', NULL, '0000000000000000E02B003000034B1C', '4e20d7e1-aca6-4fa9-9de5-60dbef49db9b', '2016-08-03 12:44:22'),
  ('661e1c8e-9da7-44d2-aa9c-074cb38c7f0e', '20', 'EndRound', '2016-08-03 12:45:48', '009', NULL, '0000000000000000E02B003000034B1C', '3a87a71e-ee52-4ef9-abff-fa01d94a56ce', '2016-08-03 12:46:26'),
  ('638fd4a9-8b2d-4b12-9a60-dd34363817b3', '20', 'EndRound', '2016-08-03 12:47:02', '009', NULL, '0000000000000000E02B003000034B1C', 'fef5119a-5c54-486c-b4bc-b5af0b085abb', '2016-08-03 12:47:22'),
  ('d4f44d54-8be3-4555-8ebc-a09d3319df95', '20', 'EndRound', '2016-08-03 12:48:01', '009', NULL, '0000000000000000E02B003000034B1C', 'e27ead46-bde0-4f3d-86ac-de81ca08c3c9', '2016-08-03 12:48:22'),
  ('6733fdd1-11f3-4135-9be5-03074fb78dc8', '20', 'EndRound', '2016-08-03 12:49:40', '009', NULL, '0000000000000000E02B003000034B1C', 'ceac72ba-ae99-4d35-b269-76ab3566c887', '2016-08-03 12:49:52'),
  ('61743634-7a5a-42a2-b8c3-fd302885b7a2', '20', 'EndRound', '2016-08-03 12:51:01', '009', NULL, '0000000000000000E02B003000034B1C', '526769a6-158c-4e9c-b414-6415bb80b89e', '2016-08-03 12:51:22'),
  ('8b49ca52-d379-424a-a585-1d7a7f4b6d4d', '20', 'EndRound', '2016-08-03 12:51:59', '009', NULL, '0000000000000000E02B003000034B1C', 'd09f2e8f-a2c6-49a4-bde0-ed4211f49cf6', '2016-08-03 12:52:22'),
  ('fc7fbbd2-8fca-4adb-ae20-33d6fd6ba395', '20', 'EndRound', '2016-08-03 12:52:58', '009', NULL, '0000000000000000E02B003000034B1C', 'b61a87b1-0ed7-4fd9-9441-6cd6a3ceaa34', '2016-08-03 12:53:21'),
  ('60e92451-878a-43b1-97b1-128c8829aed8', '20', 'EndRound', '2016-08-03 12:53:58', '009', NULL, '0000000000000000E02B003000034B1C', 'cc73d0ba-46b2-4a00-b104-9bb4b19f14a0', '2016-08-03 12:54:22'),
  ('f0543e65-2862-43b6-8e4b-223edb1e8188', '20', 'EndRound', '2016-08-03 12:54:54', '009', NULL, '0000000000000000E02B003000034B1C', '3ae7fe30-0350-4701-bec5-cd9d0a21fe29', '2016-08-03 12:55:21'),
  ('c981dc81-af19-43b7-810c-7adecee34849', '20', 'EndRound', '2016-08-03 12:55:59', '009', NULL, '0000000000000000E02B003000034B1C', 'a540a069-ec7a-4213-9402-5a5098dcb1d6', '2016-08-03 12:56:23'),
  ('af80048f-e45a-4cfb-8706-05486ba864d3', '20', 'EndRound', '2016-08-03 12:56:56', '009', NULL, '0000000000000000E02B003000034B1C', 'f2bd3aa9-b0c2-4b97-8045-486bc37821db', '2016-08-03 12:57:22'),
  ('aac29444-fb48-4f0f-bf03-4286f2ba244b', '20', 'EndRound', '2016-08-03 12:57:58', '009', NULL, '0000000000000000E02B003000034B1C', 'b6d61556-b277-4ec8-aa8c-31e1b74f24c0', '2016-08-03 12:58:22'),
  ('c3d68e77-e99d-4531-a300-3ddce2d6cf9b', '20', 'EndRound', '2016-08-03 12:58:59', '009', NULL, '0000000000000000E02B003000034B1C', '399f5ac5-0598-459e-9760-6297e93ecc77', '2016-08-03 12:59:22'),
  ('bdfd5eb6-082f-49ac-b075-2cf6071b8949', '20', 'EndRound', '2016-08-03 13:00:12', '009', NULL, '0000000000000000E02B003000034B1C', '608699d4-15e2-4827-a085-e33bad1ddea2', '2016-08-03 13:00:22'),
  ('f487bb0d-3ee4-4187-b4e5-18cfdca79ce9', '20', 'EndRound', '2016-08-03 13:01:27', '009', NULL, '0000000000000000E02B003000034B1C', 'bf67b5b3-5bfc-47fc-a2f4-5cdf2e9ea3f1', '2016-08-03 13:01:51'),
  ('dfbd46b0-920b-49f3-9d4d-b6a660007616', '20', 'EndRound', '2016-08-03 13:02:26', '009', NULL, '0000000000000000E02B003000034B1C', 'bf186d28-0b3a-4f4b-b4ff-7b3da41c55be', '2016-08-03 13:02:51'),
  ('ce14bf07-3ace-4c3a-a4e0-96aebc09b7c8', '20', 'EndRound', '2016-08-03 13:03:44', '009', NULL, '0000000000000000E02B003000034B1C', 'e7ff741e-792e-4651-9ddf-22a1567ac483', '2016-08-03 13:04:21'),
  ('ba01c9e2-cc9b-45e7-b0c9-beb208c76a8e', '20', 'EndRound', '2016-08-03 13:04:40', '009', NULL, '0000000000000000E02B003000034B1C', '76230d8d-7808-4aef-955c-429f56cdcf4c', '2016-08-03 13:04:53'),
  ('b312164d-cf3b-491d-95f9-06f1fdb447ad', '20', 'EndRound', '2016-08-03 13:05:37', '009', NULL, '0000000000000000E02B003000034B1C', '70697ada-e815-40a1-8861-6a53bdbba852', '2016-08-03 13:05:52'),
  ('532102e8-3fcb-4172-b856-936b5630266d', '20', 'EndRound', '2016-08-03 13:07:08', '009', NULL, '0000000000000000E02B003000034B1C', '3a7a3888-d5cd-4297-bb22-7e2f4cd94c1c', '2016-08-03 13:07:22'),
  ('e25aa19e-a5f9-4e23-92cc-d3c74bbc28d7', '20', 'EndRound', '2016-08-03 13:07:48', '009', NULL, '0000000000000000E02B003000034B1C', '8212ac1e-056e-452d-bb40-5f403e67594d', '2016-08-03 13:08:21'),
  ('64f25759-69f7-4f52-a135-28eb7535d66f', '20', 'EndRound', '2016-08-03 13:08:27', '009', NULL, '0000000000000000E02B003000034B1C', '3d328af9-e393-46d8-bcfb-afcf4365d907', '2016-08-03 13:08:52'),
  ('0419406c-4287-4876-b3eb-b1e149b72f81', '20', 'EndRound', '2016-08-03 13:09:34', '009', NULL, '0000000000000000E02B003000034B1C', '97f8fb86-235e-4e42-8a31-79a840538cc9', '2016-08-03 13:09:52'),
  ('8ba374de-52a2-4fbf-a240-ddd505d4de38', '20', 'EndRound', '2016-08-03 13:10:19', '009', NULL, '0000000000000000E02B003000034B1C', 'a802bcf6-6b0a-4568-aed5-9691ed8d6a1c', '2016-08-03 13:10:51'),
  ('393ce0df-916c-4e21-be1f-7ac0d0b17579', '20', 'EndRound', '2016-08-03 13:11:49', '009', NULL, '0000000000000000E02B003000034B1C', '02e46db5-8b11-45a6-b5e6-4513fe45825c', '2016-08-03 13:12:21'),
  ('59db1c05-abcf-46fd-89eb-86055acee8c6', '20', 'EndRound', '2016-08-03 13:13:02', '009', NULL, '0000000000000000E02B003000034B1C', 'd10292e6-3a54-48b3-a8ac-51d6373fc84a', '2016-08-03 13:13:22'),
  ('1c82c1ee-e0c7-4f5a-974d-bacab5bf1205', '20', 'EndRound', '2016-08-03 13:13:47', '009', NULL, '0000000000000000E02B003000034B1C', 'fb429be8-b6cf-4843-97e8-9de0258cfc52', '2016-08-03 13:13:52'),
  ('b7e14463-ed6b-42f0-b370-c96e506f07e8', '20', 'EndRound', '2016-08-03 13:14:30', '009', NULL, '0000000000000000E02B003000034B1C', '42e9b4e8-e3e2-41bf-bdec-9e006d535f78', '2016-08-03 13:14:51'),
  ('69ddce79-e588-4f17-875d-78b17e25954a', '20', 'EndRound', '2016-08-03 13:15:30', '009', NULL, '0000000000000000E02B003000034B1C', '3f407f1d-6e83-485f-af14-d5c5174f90b7', '2016-08-03 13:15:51'),
  ('92dd7b83-a61c-4cff-948c-fba918dfa51b', '20', 'DealerLogout', '2016-08-03 13:17:31', '009', '009', 'stesala', 'd481f6ec-6a1d-4519-ba79-9dd417b737f2', '2016-08-03 13:17:51'),
  ('57ed8887-7e82-4e74-9e2f-550856bcfd03', '20', 'EndRound', '2016-08-03 13:17:27', '009', NULL, '0000000000000000E02B003000034B1C', 'dabef94b-1733-419f-acc3-8277c906fff6', '2016-08-03 13:17:52');

DROP TABLE IF EXISTS audit_dead_spread;
CREATE TABLE `audit_dead_spread` (
  `dealer_down_id` varchar(50) NOT NULL DEFAULT '',
  `audit` longtext DEFAULT NULL,
  `audit_ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`dealer_down_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
