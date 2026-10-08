type PalletProps = {
    position?: [number, number, number]
}

export default function Pallet({ position = [0, 0, 0] }: PalletProps) {
    return (
        <group position={position}>
            {/* Đế pallet gỗ */}
            <mesh position={[0, 0.07, 0]} castShadow>
                <boxGeometry args={[1.4, 0.14, 1.1]} />
                <meshStandardMaterial
                    color="#d97706"
                    roughness={0.9}
                />
            </mesh>

            {/* Kiện hàng */}
            <mesh position={[0, 0.59, 0]} castShadow>
                <boxGeometry args={[1.2, 0.9, 0.9]} />
                <meshStandardMaterial
                    color="#0284c7"
                    roughness={0.5}
                />
            </mesh>
        </group>
    )
}