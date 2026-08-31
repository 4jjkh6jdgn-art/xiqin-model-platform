import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import fs from 'fs';

const glbPath = process.argv[2];
if (!glbPath) { console.error('用法: node test-gltf-load.mjs <glb路径>'); process.exit(1); }

const buffer = fs.readFileSync(glbPath);
const arrayBuffer = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);

const loader = new GLTFLoader();
console.log('开始解析 GLB...');
const start = Date.now();
loader.parse(arrayBuffer, '', (gltf) => {
  console.log(`解析完成，耗时 ${Date.now() - start}ms`);
  gltf.scene.traverse((child) => {
    if (!child.isMesh) return;
    const mats = Array.isArray(child.material) ? child.material : [child.material];
    mats.forEach((mat) => {
      ['map','normalMap','roughnessMap','metalnessMap'].forEach((key) => {
        const tex = mat[key];
        if (tex && tex.isTexture) {
          console.log(`${key}: image type=${tex.image?.constructor?.name} width=${tex.image?.width} height=${tex.image?.height} src=${(tex.image?.src||'').slice(0,60)}`);
        }
      });
    });
  });
}, (err) => {
  console.error('解析失败:', err);
});
