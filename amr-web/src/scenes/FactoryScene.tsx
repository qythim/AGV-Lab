import { Canvas } from '@react-three/fiber'
import { Grid, OrbitControls } from '@react-three/drei'

import Rack from '../components/objects/Rack'
import Pallet from '../components/objects/Pallet'
import Dock from '../components/objects/Dock'
import Barrier from '../components/objects/Barrier'
import AGV from '../components/objects/Agv'

export default function FactoryScene() {
  return (
    <Canvas camera={{ position: [12, 12, 12], fov: 50 }} shadows>
      <ambientLight intensity={1.5} />
      <directionalLight position={[10, 15, 10]} intensity={2} castShadow />

      <Grid args={[30, 30]} />

      <Rack position={[-4, 0, 0]} />
      <Pallet position={[0, 0, 4]} />
      <Dock position={[5, 0, 0]} />
      <Barrier position={[0, 0, -4]} />
      <AGV position={[0, 0, 0]} />

      <OrbitControls />
    </Canvas>
  )
}