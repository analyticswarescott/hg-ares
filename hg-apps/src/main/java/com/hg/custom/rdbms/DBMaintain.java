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


        //configured for single target db with site ID as tenant
        Map<String, String> dbc = new HashMap<>();
        dbc.put(DBConfig.DB_HOST, "localhost");
        dbc.put(DBConfig.DB_PORT, "3306");
        dbc.put(DBConfig.DB_USER, "root");
        dbc.put(DBConfig.DB_PASS, "");
        dbc.put(DBConfig.DB_SCHEMA, "test_bi");

        String url = provider.getJDBCURL(dbc);

        try (Connection conn = DBMgr.getConnection(url, dbc.get(DBConfig.DB_USER), dbc.get(DBConfig.DB_PASS))) {

            Statement stmt = conn.createStatement();
            stmt.execute(ddl);

        }




    }


}
