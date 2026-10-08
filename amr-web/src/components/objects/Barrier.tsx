type BarrierProps = {
    position?: [number, number, number]
}

export default function Barrier({
    position = [0, 0, 0],
}: BarrierProps) {
    return (
        <group position={position}>
            <mesh position={[0, 0.3, 0]} castShadow>
                <boxGeometry args={[2, 0.6, 0.2]} />
                <meshStandardMaterial
                    color="#ef4444"
                    roughness={0.4}
                />
            </mesh>
        </group>
    )
}