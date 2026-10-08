import { useState } from 'react'
import heroImg from './assets/hero.png'
import reactLogo from './assets/react.svg'
import viteLogo from './assets/vite.svg'
import './App.css'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'
function App() {
  const [count, setCount] = useState(0)

  return (
    <div style={{ width: '100vh', height: '100vh' }}>
      <Canvas camera={{ position: [10, 10, 10] }}>
        <ambientLight intensity={1} />

        <Grid args={[20, 20]} />

        <mesh position={[0, 0.5, 0]}>
          <boxGeometry args={[1, 1, 1]} />
          <meshStandardMaterial />
        </mesh>

        <OrbitControls />
      </Canvas>
    </div>
  )
}

export default App
