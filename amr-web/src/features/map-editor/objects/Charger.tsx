// ============================================================================
// 檔案名稱：Charger.tsx
// 模組說明：AGV 充電樁 3D 物件元件，帶有充電指示燈光效果
// ============================================================================

/** 充電樁元件屬性定義 */
interface ChargerProps {
  position: [number, number, number]
  rotationY?: number
  size: { length: number; width: number; height: number }
}

export default function Charger({ position, rotationY = 0, size }: ChargerProps) {
  const { length, width, height } = size

  return (
    <group position={position} rotation={[0, rotationY, 0]}>
      {/* 充電站底座平台 */}
      <mesh position={[0, -height / 2 + 0.06, 0]} receiveShadow>
        <boxGeometry args={[length + 0.3, 0.12, width + 0.3]} />
        <meshStandardMaterial color="#10b981" metalness={0.5} roughness={0.4} />
      </mesh>

      {/* 充電主機箱體 */}
      <mesh castShadow>
        <boxGeometry args={[length, height, width]} />
        <meshStandardMaterial color="#064e3b" metalness={0.6} roughness={0.3} />
      </mesh>

      {/* 前面板顯示區 */}
      <mesh position={[0, height * 0.1, width / 2 + 0.01]}>
        <boxGeometry args={[length * 0.7, height * 0.5, 0.02]} />
        <meshStandardMaterial color="#10b981" emissive="#10b981" emissiveIntensity={0.4} />
      </mesh>

      {/* 充電指示燈點光源 */}
      <pointLight
        color="#10b981"
        intensity={12}
        distance={5}
        position={[0, height / 2 + 0.3, 0]}
      />

      {/* 頂部指示燈帽 */}
      <mesh position={[0, height / 2 + 0.1, 0]}>
        <cylinderGeometry args={[0.08, 0.08, 0.2, 12]} />
        <meshStandardMaterial color="#10b981" emissive="#10b981" emissiveIntensity={0.8} />
      </mesh>
    </group>
  )
}
