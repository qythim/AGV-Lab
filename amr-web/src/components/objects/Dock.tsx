type DockProps = {
  position?: [number, number, number]
}

export default function Dock({ position = [0, 0, 0] }: DockProps) {
  return (
    <group position={position}>
      {/* Mặt đệm sàn */}
      <mesh
        position={[0, 0.02, 0]}
        rotation={[-Math.PI / 2, 0, 0]}
      >
        <planeGeometry args={[3, 3]} />
        <meshBasicMaterial
          color="#10b981"
          transparent
          opacity={0.4}
        />
      </mesh>

      {/* Cột sạc */}
      <mesh position={[0, 0.75, -1.2]}>
        <cylinderGeometry args={[0.1, 0.1, 1.5, 16]} />
        <meshStandardMaterial
          color="#10b981"
          metalness={0.8}
        />
      </mesh>

      {/* Đèn trạm sạc */}
      <pointLight
        color="#10b981"
        intensity={10}
        distance={4}
        position={[0, 1.5, -1.2]}
      />
    </group>
  )
}