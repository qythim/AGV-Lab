// ============================================================================
// 檔案名稱：Agv.tsx
// 模組說明：AGV 自主移動機器人 3D 物件元件
//           包含車體底盤 (Chassis)、防撞保險桿 (Bumper)、驅動輪組 (Wheels)、
//           LiDAR 光學掃描環 (LiDAR Ring) 與狀態指示燈 (Beacon)
// ============================================================================

/** AGV 運行狀態列舉 */
export type AgvStatus = 'IDLE' | 'MOVING' | 'CHARGING' | 'ERROR'

/** AGV 元件屬性定義 */
export interface AgvProps {
  /** 機器人 3D 空間坐標 [X, Y, Z] (地面高度 Y 預設為 0) */
  position?: [number, number, number]
  /** Y 軸旋轉角度 (弧度，預設 0) */
  rotationY?: number
  /** AGV 車輛識別代碼 (例：'AGV_01') */
  agvCode?: string
  /** 車輛運行狀態 (決定指示燈顏色) */
  status?: AgvStatus
}

/** 依運行狀態設定指示燈顏色 */
const STATUS_COLORS: Record<AgvStatus, string> = {
  IDLE:     '#10b981', // 待機：綠色
  MOVING:   '#38bdf8', // 運行中：天藍色
  CHARGING: '#FF8C00', // 充電中：主題橘色
  ERROR:    '#ef4444', // 異常報警：紅色
}

export default function Agv({
  position = [0, 0, 0],
  rotationY = 0,
  agvCode = 'AGV_01',
  status = 'IDLE',
}: AgvProps) {
  const beaconColor = STATUS_COLORS[status] ?? STATUS_COLORS.IDLE

  return (
    <group name={agvCode} position={position} rotation={[0, rotationY, 0]}>
      {/* ── 1. 主車體底盤 (Chassis) ─────────────────────────────────── */}
      <mesh position={[0, 0.28, 0]} castShadow receiveShadow>
        <boxGeometry args={[1.6, 0.36, 1.0]} />
        <meshStandardMaterial
          color="#FF8C00"
          metalness={0.65}
          roughness={0.25}
        />
      </mesh>

      {/* 頂部防滑載貨面板 */}
      <mesh position={[0, 0.47, 0]} receiveShadow>
        <boxGeometry args={[1.5, 0.03, 0.9]} />
        <meshStandardMaterial
          color="#0f172a"
          roughness={0.8}
          metalness={0.2}
        />
      </mesh>

      {/* ── 2. 環車防撞保險桿 (Safety Bumper) ────────────────────────── */}
      <mesh position={[0, 0.12, 0]}>
        <boxGeometry args={[1.68, 0.14, 1.08]} />
        <meshStandardMaterial
          color="#1e293b"
          roughness={0.6}
          metalness={0.3}
        />
      </mesh>

      {/* ── 3. 左右差速驅動輪 (Drive Wheels) ─────────────────────────── */}
      {/* 左輪 */}
      <mesh position={[0, 0.14, 0.52]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[0.14, 0.14, 0.08, 24]} />
        <meshStandardMaterial color="#090d16" roughness={0.9} />
      </mesh>
      {/* 右輪 */}
      <mesh position={[0, 0.14, -0.52]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[0.14, 0.14, 0.08, 24]} />
        <meshStandardMaterial color="#090d16" roughness={0.9} />
      </mesh>

      {/* 前後萬向輔助支撐輪 (Casters) */}
      <mesh position={[0.65, 0.08, 0]}>
        <sphereGeometry args={[0.08, 16, 16]} />
        <meshStandardMaterial color="#475569" metalness={0.8} />
      </mesh>
      <mesh position={[-0.65, 0.08, 0]}>
        <sphereGeometry args={[0.08, 16, 16]} />
        <meshStandardMaterial color="#475569" metalness={0.8} />
      </mesh>

      {/* ── 4. LiDAR 360 度安全光學掃描環 (LiDAR Ring) ───────────────── */}
      <mesh position={[0, 0.04, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[1.4, 1.48, 48]} />
        <meshBasicMaterial
          color="#06b6d4"
          side={2}
          transparent
          opacity={0.45}
        />
      </mesh>

      {/* LiDAR 旋轉掃描頭主體 */}
      <mesh position={[0.7, 0.32, 0]}>
        <cylinderGeometry args={[0.07, 0.07, 0.12, 16]} />
        <meshStandardMaterial color="#0284c7" metalness={0.8} roughness={0.2} />
      </mesh>

      {/* ── 5. 車身狀態指示燈塔 (Status Beacon) ──────────────────────── */}
      <mesh position={[-0.6, 0.54, 0]}>
        <cylinderGeometry args={[0.06, 0.06, 0.12, 16]} />
        <meshStandardMaterial
          color={beaconColor}
          emissive={beaconColor}
          emissiveIntensity={0.8}
        />
      </mesh>
      {/* 狀態燈光源 (向周圍擴散微光) */}
      <pointLight
        color={beaconColor}
        intensity={2.5}
        distance={2.5}
        position={[-0.6, 0.65, 0]}
      />
    </group>
  )
}
