type RackProps = {
    position?: [number, number, number]
}

export default function Rack({ position = [0, 0, 0] }: RackProps) {
    const posts: [number, number][] = [
        [-1.2, 0.6],
        [1.2, 0.6],
        [-1.2, -0.6],
        [1.2, -0.6],
    ]

    const shelves = [0.6, 1.8, 3.0]

    return (
        <group position={position}>
            {/* 4 cột trụ */}
            {posts.map(([x, z], i) => (
                <mesh key={i} position={[x, 1.9, z]} castShadow>
                    <boxGeometry args={[0.12, 3.8, 0.12]} />
                    <meshStandardMaterial
                        color="#2563eb"
                        metalness={0.6}
                        roughness={0.3}
                    />
                </mesh>
            ))}

            {/* 3 tầng kệ và thùng hàng */}
            {shelves.map((y) => (
                <group key={y}>
                    <mesh position={[0, y, 0]} castShadow>
                        <boxGeometry args={[2.6, 0.08, 1.4]} />
                        <meshStandardMaterial
                            color="#f59e0b"
                            metalness={0.4}
                            roughness={0.4}
                        />
                    </mesh>

                    {[-0.6, 0.6].map((x) => (
                        <mesh key={x} position={[x, y + 0.34, 0]} castShadow>
                            <boxGeometry args={[0.8, 0.6, 0.8]} />
                            <meshStandardMaterial
                                color="#b45309"
                                roughness={0.8}
                            />
                        </mesh>
                    ))}
                </group>
            ))}
        </group>
    )
}