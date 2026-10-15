// ============================================================================
// 檔案名稱：Station.tsx
// 模組說明：工作站 / 操作台 3D 物件元件，代表人員與 AGV 的交匯作業點
// ============================================================================

/** 工作站元件屬性定義 */
interface StationProps {
  position: [number, number, number]
  rotationY?: number
  size: { length: number; width: number; height: number }
}

export default function Station({ position, rotationY = 0, size }: StationProps) {
  const { length, width, height } = size

  return (
    <group position={position} rotation={[0, rotationY, 0]}>
      {/* 工作台本體 */}
      <mesh castShadow receiveShadow>
        <boxGeometry args={[length, height, width]} />
        <meshStandardMaterial color="#1e3a5f" metalness={0.5} roughness={0.4} />
      </mesh>

      {/* 台面板 */}
      <mesh position={[0, height / 2 + 0.03, 0]} castShadow>
        <boxGeometry args={[length + 0.05, 0.06, width + 0.05]} />
        <meshStandardMaterial color="#FF8C00" metalness={0.3} roughness={0.5} />
      </mesh>

      {/* 顯示幕指示燈 */}
      <mesh position={[0, height / 2 + 0.2, -width / 2 + 0.1]}>
        <boxGeometry args={[length * 0.5, 0.3, 0.05]} />
        <meshStandardMaterial
          color="#0ea5e9"
          emissive="#0ea5e9"
          emissiveIntensity={0.5}
        />
      </mesh>

      {/* 工作站地板標記 */}
      <mesh position={[0, -height / 2 + 0.01, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[length + 0.6, width + 0.6]} />
        <meshBasicMaterial color="#FF8C00" transparent opacity={0.25} />
      </mesh>
    </group>
  )
}
