"use client";

import { Canvas, useFrame } from "@react-three/fiber";
import { useMemo, useRef } from "react";
import type { Points } from "three";

function Dust() {
  const ref = useRef<Points>(null);
  const positions = useMemo(() => {
    const n = 900;
    const arr = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const r = 3 + Math.random() * 6, t = Math.random() * Math.PI * 2, y = (Math.random() - 0.5) * 5;
      arr[i * 3] = Math.cos(t) * r;
      arr[i * 3 + 1] = y;
      arr[i * 3 + 2] = Math.sin(t) * r - 4;
    }
    return arr;
  }, []);
  useFrame((_, dt) => {
    if (ref.current) {
      ref.current.rotation.y += dt * 0.03;
      ref.current.rotation.x = Math.sin(performance.now() / 9000) * 0.08;
    }
  });
  return (
    <points ref={ref}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
      </bufferGeometry>
      <pointsMaterial size={0.035} color="#d9cbff" transparent opacity={0.85} sizeAttenuation depthWrite={false} />
    </points>
  );
}

/** Subtle depth layer: slowly orbiting starlight particles. Loaded lazily and skipped on low-powered devices. */
export default function HeroParticles() {
  return (
    <Canvas camera={{ position: [0, 0, 5], fov: 60 }} dpr={[1, 1.5]} gl={{ antialias: false, alpha: true, powerPreference: "low-power" }} frameloop="always">
      <Dust />
    </Canvas>
  );
}
