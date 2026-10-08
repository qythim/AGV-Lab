type AGVProps = {
    position?: [number, number, number]
    rotation?: [number, number, number]
}

export default function AGV({
    position = [0, 0, 0],
    rotation = [0, 0, 0],
}: AGVProps) {
    return (
        <group position={position} rotation={rotation}>

            {/* 1. Chassis */}
            <mesh position={[0, 0.35, 0]} castShadow>
                <boxGeometry args={[1.8, 0.5, 1.2]} />
                <meshStandardMaterial
                    color="#f97316"
                    metalness={0.7}
                    roughness={0.2}
                />
            </mesh>

            {/* 2. Bumper */}
            <mesh position={[0, 0.15, 0]}>
                <boxGeometry args={[1.9, 0.15, 1.3]} />
                <meshStandardMaterial
                    color="#1f2937"
                    roughness={0.5}
                />
            </mesh>

            {/* 3. LiDAR Ring */}
            <mesh
                position={[0, 0.05, 0]}
                rotation={[-Math.PI / 2, 0, 0]}
            >
                <ringGeometry args={[1.5, 1.55, 32]} />
                <meshBasicMaterial
                    color="#06b6d4"
                    side={2}
                    transparent
                    opacity={0.5}
                />
            </mesh>

            {/* 4. Beacon */}
            <mesh position={[-0.7, 0.65, 0]}>
                <cylinderGeometry args={[0.08, 0.08, 0.15, 8]} />
                <meshBasicMaterial color="#10b981" />
            </mesh>

        </group>
    )
}