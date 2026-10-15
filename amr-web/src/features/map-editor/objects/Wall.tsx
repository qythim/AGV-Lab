// ============================================================================
// 檔案名稱：Wall.tsx
// 模組說明：牆體 / 圍牆 3D 物件元件，依 JSON 尺寸動態渲染
// ============================================================================

/** 牆體元件屬性定義 */
interface WallProps {
  position: [number, number, number]
  rotationY?: number
  size: { length: number; width: number; height: number }
}

export default function Wall({ position, rotationY = 0, size }: WallProps) {
  const { length, width, height } = size

  return (
    <group position={position} rotation={[0, rotationY, 0]}>
      {/* 主體牆面 */}
      <mesh castShadow receiveShadow>
        <boxGeometry args={[length, height, width]} />
        <meshStandardMaterial
          color="#334155"
          roughness={0.75}
          metalness={0.1}
        />
      </mesh>
      {/* 頂部橫樑裝飾線 */}
      <mesh position={[0, height / 2, 0]}>
        <boxGeometry args={[length, 0.06, width + 0.04]} />
        <meshStandardMaterial color="#475569" metalness={0.4} />
      </mesh>
    </group>
  )
}
