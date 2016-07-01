package com.hg.custom.rdbms;

import com.aw.common.rdbms.DBConfig;
import com.aw.common.rdbms.DBMgr;
import com.aw.common.tenant.Tenant;
import com.aw.document.jdbc.mysql.MySQLJDBCProvider;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * static class for maintaining DDL
 */
public class DBMaintain {


    public static void main(String[] args) throws Exception {


        //create
        File f = new File("/Users/scott/dev/src/hg-ares/conf/rdbms/hg_bi_schema.sql");
        String ddl = FileUtils.readFileToString(f);

        MySQLJDBCProvider provider = new MySQLJDBCProvider();


        Map<String, String> dbc = new HashMap<>();
        dbc.put(DBConfig.DB_HOST, "corpdb.cho2ai5h4pd6.us-west-2.rds.amazonaws.com");
        dbc.put(DBConfig.DB_PORT, "3306");
        dbc.put(DBConfig.DB_USER, "fortissadmin");
        dbc.put(DBConfig.DB_PASS, "joker4DB!");
        dbc.put(DBConfig.DB_SCHEMA, "testbi"); //will be appended _[TENANT_ID]

        String url = provider.getJDBCURL(dbc, Tenant.forId("1"));


        try (Connection conn = DBMgr.getConnection(url, dbc.get(DBConfig.DB_USER), dbc.get(DBConfig.DB_PASS))) {

            Statement stmt = conn.createStatement();
            stmt.execute(ddl);

        }




    }


}
