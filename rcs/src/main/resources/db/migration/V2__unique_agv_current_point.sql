CREATE UNIQUE INDEX uq_rcs_agv_current_point
ON rcs_agv (current_map_code, current_point)
WHERE current_point IS NOT NULL;