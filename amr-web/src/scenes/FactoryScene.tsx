import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'

import Rack from '../components/objects/Rack'
import Pallet from '../components/objects/Pallet'
import Dock from '../components/objects/Dock'
import Barrier from '../components/objects/Barrier'
import AGV from '../components/objects/Agv'

export default function FactoryScene() {
    return (
        <Canvas
            camera={{
                position: [16, 18, 22],
                fov: 45,
                near: 0.1,
                far: 150,
            }}
            shadows
            gl={{
                antialias: true,
            }}
        >
            {/* Màu nền */}
            <color attach="background" args={['#0a0d14']} />

            {/* Sương mù tạo chiều sâu */}
            <fogExp2 attach="fog" args={['#0a0d14', 0.022]} />

            {/* Ánh sáng nền */}
            <ambientLight intensity={0.65} />

            {/* Ánh sáng chính từ trên xuống */}
            <directionalLight
                position={[15, 25, 12]}
                intensity={1.6}
                castShadow
                shadow-mapSize={[2048, 2048]}
                shadow-camera-near={1}
                shadow-camera-far={70}
                shadow-camera-left={-25}
                shadow-camera-right={25}
                shadow-camera-top={25}
                shadow-camera-bottom={-25}
            />

            {/* Ánh sáng cyan */}
            <pointLight
                color="#06b6d4"
                position={[0, 8, 0]}
                intensity={40}
                distance={30}
            />

            {/* Sàn nhà kho */}
            <mesh rotation={[-Math.PI / 2, 0, 0]} receiveShadow>
                <planeGeometry args={[60, 60]} />
                <meshStandardMaterial
                    color="#111622"
                    roughness={0.4}
                    metalness={0.2}
                />
            </mesh>

            {/* Lưới */}
            <Grid
                args={[60, 60]}
                sectionColor="#1e293b"
                cellColor="#141d2c"
                position={[0, 0.01, 0]}
            />

            {/* Các vật thể */}
            <Rack position={[-4, 0, 0]} />
            <Pallet position={[0, 0, 4]} />
            <Dock position={[5, 0, 0]} />
            <Barrier position={[0, 0, -4]} />
            <AGV position={[0, 0, 0]} />

            {/* Điều khiển camera */}
            <OrbitControls
                target={[0, 0, 0]}
                enableDamping
                dampingFactor={0.05}
                maxPolarAngle={Math.PI / 2.05}
            />
        </Canvas>
    )
}