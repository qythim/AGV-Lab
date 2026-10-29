package com.agvlab.rcs.map.repository;

import com.agvlab.rcs.map.dto.MapDataDto.MapInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** 地圖文件使用 JDBC 明確保存 bytea；業務驗證與快照建立留在 Service。 */
@Repository
public class MapDocumentRepository {
    private final JdbcTemplate jdbc;
    public MapDocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record ContentRow(long id, byte[] content, String sha256, boolean active) {}

    public void lockChanges() { jdbc.execute("SELECT pg_advisory_xact_lock(754903)"); }
    public List<String> mapCodes() {
        return jdbc.queryForList("SELECT map_code FROM rcs_map ORDER BY map_code", String.class);
    }
    public List<String> activeMapCodes() {
        return jdbc.queryForList("SELECT map_code FROM rcs_map WHERE is_active=true", String.class);
    }
    public Optional<Integer> findVersion(String code) {
        return jdbc.queryForList("SELECT version FROM rcs_map WHERE map_code=?", Integer.class, code).stream().findFirst();
    }
    public boolean isActive(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_active FROM rcs_map WHERE map_code=?", Boolean.class, code));
    }
    public Optional<ContentRow> current(String code) {
        return jdbc.query("""
                SELECT c.id,c.content,c.sha256,m.is_active FROM rcs_map m
                JOIN rcs_map_content c ON c.id=m.content_id
                WHERE (CAST(? AS text) IS NULL AND m.is_active=true) OR m.map_code=?
                """, (rs,i)->new ContentRow(rs.getLong("id"),rs.getBytes("content"),rs.getString("sha256"),rs.getBoolean("is_active")),
                code,code).stream().findFirst();
    }
    public Optional<ContentRow> version(String code, int version) {
        return jdbc.query("SELECT id,content,sha256 FROM rcs_map_content WHERE map_code=? AND version=?",
                (rs,i)->new ContentRow(rs.getLong("id"),rs.getBytes("content"),rs.getString("sha256"),false),
                code,version).stream().findFirst();
    }
    public List<Map<String,Object>> versions(String code) {
        return jdbc.queryForList("SELECT id,version,created_at FROM rcs_map_content WHERE map_code=? ORDER BY version DESC", code);
    }
    public void createMap(String code,String name) {
        jdbc.update("INSERT INTO rcs_map(map_code,map_name) VALUES (?,?)",code,name);
    }
    public long appendContent(String code,int version,byte[] content,String hash) {
        return jdbc.queryForObject("""
                INSERT INTO rcs_map_content(map_code,version,content,sha256) VALUES (?,?,?,?) RETURNING id
                """,Long.class,code,version,content,hash);
    }
    public void updateCurrent(MapInfo info,long contentId) {
        jdbc.update("""
                UPDATE rcs_map SET map_name=?,version=?,content_id=?,width=?,height=?,origin_x=?,origin_y=?,context=?,updated_at=CURRENT_TIMESTAMP
                WHERE map_code=?
                """,info.mapName(),info.version(),contentId,info.width(),info.height(),info.originX(),info.originY(),info.context(),info.mapCode());
    }
    public void activate(String code) {
        jdbc.update("UPDATE rcs_map SET is_active=false WHERE is_active=true AND map_code<>?",code);
        jdbc.update("UPDATE rcs_map SET is_active=true WHERE map_code=?",code);
    }
    public boolean hasVehicleVersionMismatch(String code,long contentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM rcs_agv WHERE current_map_code=? AND map_content_id IS DISTINCT FROM ?)
                """,Boolean.class,code,contentId));
    }
    public boolean isInUse(String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM rcs_agv WHERE current_map_code=?)
                OR EXISTS(SELECT 1 FROM rcs_task WHERE map_code=? AND status IN ('PENDING','RUNNING'))
                OR EXISTS(SELECT 1 FROM rcs_lock_point WHERE map_code=?)
                """,Boolean.class,code,code,code));
    }
}
