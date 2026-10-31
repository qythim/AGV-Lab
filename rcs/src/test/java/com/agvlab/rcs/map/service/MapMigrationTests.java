package com.agvlab.rcs.map.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@SpringBootTest
class MapMigrationTests {
    @Autowired Environment environment;
    @Test void migratesLegacyTopologyAndReferencesWithoutInventingReverseEdges() throws Exception {
        String url = environment.getRequiredProperty("spring.datasource.url");
        String user = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");
        String schema = "migration_" + UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("2").load().migrate();
        try (Connection db = DriverManager.getConnection(url,user,password); Statement st=db.createStatement()) {
            st.execute("SET search_path TO " + schema);
            st.execute("INSERT INTO rcs_map(map_code,map_name,width,height,is_active) VALUES ('M','舊地圖',20,15,true)");
            st.execute("INSERT INTO rcs_point(map_code,point_code,x,y,point_type,neighbors) VALUES ('M','A',1,1,'NORMAL','[\"B\"]'),('M','B',2,1,'NORMAL','[]')");
            st.execute("INSERT INTO rcs_agv(agv_code,agv_type,current_map_code,current_point) VALUES ('R','STANDARD','M','A')");
            st.execute("INSERT INTO rcs_task(task_code,map_code,start_point,target_point) VALUES ('T','M','A','B')");
            st.execute("INSERT INTO rcs_lock_point(map_code,point_code,agv_code,lock_type,expire_at) VALUES ('M','A','R','OCCUPIED',CURRENT_TIMESTAMP+INTERVAL '1 hour')");
            st.execute("INSERT INTO rcs_map_object(map_code,object_type,pos_x,pos_y,length,width,height) VALUES ('M','WALL',10,10,1,2,3)");
        }
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
        try (Connection db=DriverManager.getConnection(url,user,password); Statement st=db.createStatement()) {
            st.execute("SET search_path TO " + schema);
            try (ResultSet rs=st.executeQuery("SELECT content,legacy_snapshot FROM rcs_map_content")) {
                assertTrue(rs.next());
                String json;
                try(var gz=new GZIPInputStream(new ByteArrayInputStream(rs.getBytes(1)))) {
                    json=new String(gz.readAllBytes(),StandardCharsets.UTF_8);
                }
                var doc=JsonMapper.builder().build().readTree(json);
                assertEquals(2,doc.get("pointList").size());
                assertEquals(1,doc.get("lineList").size());
                assertEquals("A",doc.get("lineList").get(0).get("startPointCode").asString());
                assertEquals("B",doc.get("lineList").get(0).get("endPointCode").asString());
                assertEquals(1,doc.get("objects").size());
                assertNotNull(rs.getString(2));
            }
            for (String table : new String[]{"rcs_agv","rcs_task","rcs_lock_point"}) {
                try(ResultSet rs=st.executeQuery("SELECT map_content_id FROM "+table)) { assertTrue(rs.next()); assertNotNull(rs.getObject(1)); }
            }
            try(ResultSet rs=st.executeQuery("SELECT to_regclass('rcs_point'),to_regclass('rcs_map_object')")) {
                rs.next(); assertNull(rs.getObject(1)); assertNull(rs.getObject(2));
            }
        }
    }
}
