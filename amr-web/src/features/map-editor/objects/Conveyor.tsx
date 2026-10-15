// ============================================================================
// 檔案名稱：Conveyor.tsx
// 模組說明：輸送帶 3D 物件元件，呈現帶式輸送系統的外觀
// ============================================================================

/** 輸送帶元件屬性定義 */
interface ConveyorProps {
  position: [number, number, number]
  rotationY?: number
  size: { length: number; width: number; height: number }
}

/** 輸送帶滾輪數量 (每公尺 2 個) */
const ROLLER_DENSITY = 2

export default function Conveyor({ position, rotationY = 0, size }: ConveyorProps) {
  const { length, width, height } = size

  // 計算滾輪位置 (沿 X 軸均布)
  const rollerCount = Math.max(2, Math.floor(length * ROLLER_DENSITY))
  const rollerPositions = Array.from({ length: rollerCount }, (_, i) => {
    return -length / 2 + (length / (rollerCount - 1)) * i
  })

  return (
    <group position={position} rotation={[0, rotationY, 0]}>
      {/* 輸送帶主框架 */}
      <mesh castShadow receiveShadow>
        <boxGeometry args={[length, height, width]} />
        <meshStandardMaterial color="#374151" metalness={0.7} roughness={0.3} />
      </mesh>

      {/* 帶面 (傳送帶面板) */}
      <mesh position={[0, height / 2 + 0.01, 0]}>
        <boxGeometry args={[length - 0.1, 0.04, width - 0.1]} />
        <meshStandardMaterial color="#1f2937" roughness={0.9} />
      </mesh>

      {/* 滾輪群 */}
      {rollerPositions.map((rx, i) => (
        <mesh key={i} position={[rx, height / 2 + 0.02, 0]} rotation={[0, 0, Math.PI / 2]}>
          <cylinderGeometry args={[width * 0.35, width * 0.35, width * 0.85, 12]} />
          <meshStandardMaterial color="#6b7280" metalness={0.8} roughness={0.2} />
        </mesh>
      ))}

      {/* 側邊護欄 */}
      {[-width / 2, width / 2].map((zOffset, i) => (
        <mesh key={`rail-${i}`} position={[0, height / 2 + 0.1, zOffset]}>
          <boxGeometry args={[length, 0.15, 0.04]} />
          <meshStandardMaterial color="#9ca3af" metalness={0.6} />
        </mesh>
      ))}
    </group>
  )
}
