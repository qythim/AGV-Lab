// ============================================================================
// 檔案名稱：Rack.tsx
// 模組說明：貨架 3D 物件元件，支援動態尺寸與多層數渲染
// ============================================================================

/** 貨架元件屬性定義 */
interface RackProps {
  /** Three.js 空間中心點坐標 [x, y, z] */
  position: [number, number, number]
  /** Y 軸旋轉角度 (弧度) */
  rotationY?: number
  /** 幾何尺寸 */
  size: { length: number; width: number; height: number }
  /** 貨架層數 (預設 3 層) */
  rackLevels?: number
}

/** 立柱寬度 (固定值，不隨貨架縮放) */
const POST_THICKNESS = 0.1
/** 層板厚度 */
const SHELF_THICKNESS = 0.06

export default function Rack({
  position,
  rotationY = 0,
  size,
  rackLevels = 3,
}: RackProps) {
  const { length, width, height } = size

  // 計算各層板的 Y 高度位置 (等距分布)
  const shelfYPositions = Array.from({ length: rackLevels }, (_, i) => {
    const baseY = -height / 2 // 底部相對中心偏移
    return baseY + (height / (rackLevels + 1)) * (i + 1)
  })

  // 四個角柱的 XZ 偏移位置
  const postOffsets: [number, number][] = [
    [-length / 2 + POST_THICKNESS / 2, -width / 2 + POST_THICKNESS / 2],
    [ length / 2 - POST_THICKNESS / 2, -width / 2 + POST_THICKNESS / 2],
    [-length / 2 + POST_THICKNESS / 2,  width / 2 - POST_THICKNESS / 2],
    [ length / 2 - POST_THICKNESS / 2,  width / 2 - POST_THICKNESS / 2],
  ]

  return (
    <group position={position} rotation={[0, rotationY, 0]}>
      {/* 四根角柱 */}
      {postOffsets.map(([ox, oz], i) => (
        <mesh key={`post-${i}`} position={[ox, 0, oz]} castShadow>
          <boxGeometry args={[POST_THICKNESS, height, POST_THICKNESS]} />
          <meshStandardMaterial color="#2563eb" metalness={0.65} roughness={0.25} />
        </mesh>
      ))}

      {/* 各層層板 */}
      {shelfYPositions.map((shelfY, i) => (
        <group key={`shelf-${i}`}>
          {/* 層板本體 */}
          <mesh position={[0, shelfY, 0]} castShadow>
            <boxGeometry args={[length - POST_THICKNESS, SHELF_THICKNESS, width - POST_THICKNESS]} />
            <meshStandardMaterial color="#f59e0b" metalness={0.4} roughness={0.4} />
          </mesh>
          {/* 層板上的貨物 */}
          {[-length / 4, length / 4].filter(() => length > 1).map((bx, j) => (
            <mesh key={`box-${j}`} position={[bx, shelfY + SHELF_THICKNESS / 2 + 0.15, 0]} castShadow>
              <boxGeometry args={[length / 3, 0.28, width * 0.7]} />
              <meshStandardMaterial color="#b45309" roughness={0.8} />
            </mesh>
          ))}
        </group>
      ))}
    </group>
  )
}
