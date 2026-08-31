/**
 * 重新转换轺车模型：修复FBXLoader产生的重叠mesh问题
 * 
 * 问题：FBXLoader将多材质mesh拆成多个完全重叠的几何体副本（不同材质），
 *       导出GLB后导致z-fighting和内部纹理错误。
 * 
 * 修复：检测重叠mesh，通过采样贴图UV将三角形分配到对应材质组，
 *       合并成单个多材质mesh后导出GLB。
 */
import { createCanvas, Image } from 'canvas';
import * as THREE from 'three';
import { FBXLoader } from 'three/examples/jsm/loaders/FBXLoader.js';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import fs from 'fs';
import path from 'path';

// ==================== Polyfills (same as converter.js) ====================
if (typeof globalThis.document === 'undefined') {
  const createImage = () => {
    const image = new Image();
    image.addEventListener = (event, handler) => {
      if (event === 'load') {
        image.onload = function () {
          const check = () => {
            if (image.width > 0 && image.height > 0) handler.call(image);
            else setTimeout(check, 10);
          };
          check();
        };
      }
      if (event === 'error') image.onerror = handler;
    };
    image.removeEventListener = (event, handler) => {
      if (event === 'load' && image.onload === handler) image.onload = null;
      if (event === 'error' && image.onerror === handler) image.onerror = null;
    };
    return image;
  };
  const createCanvasElement = () => {
    const canvas = createCanvas(1, 1);
    canvas.style = {};
    canvas.convertToBlob = async (options) => {
      const type = options?.type || 'image/png';
      const buffer = canvas.toBuffer(type);
      return new Blob([buffer], { type });
    };
    return canvas;
  };
  globalThis.document = {
    createElement: (name) => name === 'canvas' ? createCanvasElement() : name === 'img' ? createImage() : { style: {} },
    createElementNS: (_ns, name) => name === 'canvas' ? createCanvasElement() : name === 'img' ? createImage() : { style: {} },
  };
}
if (typeof globalThis.self === 'undefined') {
  globalThis.self = { requestAnimationFrame: () => 0, cancelAnimationFrame: () => {}, URL: globalThis.URL };
}
if (typeof globalThis.window === 'undefined') {
  globalThis.window = { URL: globalThis.URL, innerWidth: 512, innerHeight: 512 };
}

// FileReader polyfill
if (typeof globalThis.FileReader === 'undefined') {
  globalThis.FileReader = class FileReader {
    constructor() { this.result = null; this.onloadend = null; this.onerror = null; }
    _emit() { if (this.onloadend) this.onloadend({ target: this }); }
    readAsArrayBuffer(blob) {
      blob.arrayBuffer().then((ab) => { this.result = ab; this._emit(); })
        .catch((err) => { if (this.onerror) this.onerror(err); });
    }
    readAsDataURL(blob) {
      blob.arrayBuffer().then((ab) => {
        this.result = `data:${blob.type || 'application/octet-stream'};base64,${Buffer.from(ab).toString('base64')}`;
        this._emit();
      }).catch((err) => { if (this.onerror) this.onerror(err); });
    }
  };
}

// ==================== Texture helpers ====================
const CHANNEL_PATTERN = /(?:^|[_ .-])(BASE[ _-]?COLOR|BASECOLOR|DIFFUSE|ALBEDO|COLOR|NORMAL|NORMALGL|NORMALDX|BUMP|METALLIC|METALNESS|METAL|ROUGHNESS|ROUGH|AO|OCCLUSION|AMBIENT|EMISSIVE|EMISSION|OPACITY|ALPHA|DISPLACEMENT|HEIGHT|DISP|D|N|M|R|E|A)$/i;

function getTextureChannel(name) {
  const base = (name || '').replace(/\.[^.]+$/, '');
  const match = base.match(CHANNEL_PATTERN);
  if (!match) return null;
  const token = match[1].replace(/[ _-]/g, '').toUpperCase();
  if (['D', 'DIFFUSE', 'ALBEDO', 'BASECOLOR', 'COLOR'].includes(token)) return 'diffuse';
  if (['N', 'NORMAL', 'NORMALGL', 'NORMALDX', 'BUMP'].includes(token)) return 'normal';
  if (['M', 'METALLIC', 'METALNESS', 'METAL'].includes(token)) return 'metalness';
  if (['R', 'ROUGHNESS', 'ROUGH'].includes(token)) return 'roughness';
  return null;
}

function normalizeAssetCore(name) {
  let base = (name || '').replace(/\.[^.]+$/, '').trim();
  base = base.replace(/^(SM|SK|T|M|MI|MAT|MTL|TEX)[ _.-]+/i, '');
  base = base.replace(CHANNEL_PATTERN, '');
  return base.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]+/g, '');
}

function coreScore(materialCore, textureCore) {
  if (!materialCore || !textureCore) return -1;
  if (materialCore === textureCore) return 1000;
  const materialNumbers = materialCore.match(/\d+/g) || [];
  const textureNumbers = textureCore.match(/\d+/g) || [];
  if (materialNumbers.length && textureNumbers.length && materialNumbers.join(',') !== textureNumbers.join(',')) return -1;
  if (materialCore.includes(textureCore) || textureCore.includes(materialCore)) return 500 + Math.min(materialCore.length, textureCore.length);
  const materialText = materialCore.replace(/\d+/g, '');
  const textureText = textureCore.replace(/\d+/g, '');
  if (materialText && materialText === textureText) return 400;
  return -1;
}

function matchTextureChannels(materialName, textureFiles) {
  const materialCore = normalizeAssetCore(materialName);
  const groups = new Map();
  textureFiles.forEach((file) => {
    const channel = getTextureChannel(file.fileName);
    if (!channel) return;
    const core = normalizeAssetCore(file.fileName);
    if (!groups.has(core)) groups.set(core, []);
    groups.get(core).push(file);
  });
  const best = [...groups.entries()]
    .map(([core, group]) => ({ core, group, score: coreScore(materialCore, core) }))
    .filter((item) => item.score >= 0)
    .sort((a, b) => b.score - a.score || b.group.length - a.group.length)[0];
  if (!best) return {};
  const channels = {};
  best.group.forEach((file) => {
    const channel = getTextureChannel(file.fileName);
    if (channel) channels[channel] = file;
  });
  return channels;
}

function createTextureFromBuffer(buffer, fileName) {
  const img = new Image();
  img.src = buffer;
  const texture = new THREE.Texture(img);
  texture.name = fileName;
  texture.flipY = true;
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.minFilter = THREE.LinearMipmapLinearFilter;
  texture.magFilter = THREE.LinearFilter;
  texture.generateMipmaps = true;
  const channel = getTextureChannel(fileName);
  if (channel === 'diffuse' || channel === 'emissive') texture.colorSpace = THREE.SRGBColorSpace;
  else texture.colorSpace = THREE.LinearSRGBColorSpace;
  return texture;
}

function applyTexturesToModel(object, textureFiles) {
  if (!textureFiles || textureFiles.length === 0) return;
  object.traverse((child) => {
    if (!child.isMesh) return;
    const materials = Array.isArray(child.material) ? child.material : [child.material];
    materials.forEach((mat) => {
      if (!mat) return;
      const channels = matchTextureChannels(mat.name || '', textureFiles);
      if (Object.keys(channels).length === 0) return;
      if (channels.diffuse) {
        mat.map = createTextureFromBuffer(channels.diffuse.buffer, channels.diffuse.fileName);
        if (mat.color) mat.color.set(0xffffff);
      }
      if (channels.normal) {
        mat.normalMap = createTextureFromBuffer(channels.normal.buffer, channels.normal.fileName);
        if (mat.normalScale) mat.normalScale.set(1, -1);
      }
      if (channels.metalness) {
        mat.metalnessMap = createTextureFromBuffer(channels.metalness.buffer, channels.metalness.fileName);
        mat.metalness = 1.0;
      }
      if (channels.roughness) {
        mat.roughnessMap = createTextureFromBuffer(channels.roughness.buffer, channels.roughness.fileName);
        mat.roughness = 1.0;
      }
      mat.needsUpdate = true;
    });
  });
}

// ==================== Overlap mesh merge ====================

/**
 * 将贴图绘制到canvas并返回2D上下文，用于像素采样
 */
function textureToCanvas(texture) {
  if (!texture || !texture.image) return null;
  const img = texture.image;
  const w = img.width || 256;
  const h = img.height || 256;
  const canvas = createCanvas(w, h);
  const ctx = canvas.getContext('2d');
  try {
    ctx.drawImage(img, 0, 0, w, h);
  } catch (e) {
    console.warn(`  绘制贴图失败: ${e.message}`);
    return null;
  }
  return { ctx, w, h };
}

/**
 * 采样canvas中UV坐标处的颜色
 */
function sampleUV(ctxInfo, u, v) {
  if (!ctxInfo) return null;
  const { ctx, w, h } = ctxInfo;
  // RepeatWrapping: UV取模
  const uu = ((u % 1) + 1) % 1;
  const vv = ((v % 1) + 1) % 1;
  const x = Math.min(w - 1, Math.max(0, Math.floor(uu * w)));
  const y = Math.min(h - 1, Math.max(0, Math.floor((1 - vv) * h))); // flipY
  try {
    const pixel = ctx.getImageData(x, y, 1, 1).data;
    return { r: pixel[0], g: pixel[1], b: pixel[2], a: pixel[3] };
  } catch (e) {
    return null;
  }
}

/**
 * 计算颜色的"有意义程度"：饱和度 + 亮度方差
 * 灰色/默认颜色得分低，有实际内容的颜色得分高
 */
function colorScore(c) {
  if (!c || c.a < 10) return 0;
  const max = Math.max(c.r, c.g, c.b);
  const min = Math.min(c.r, c.g, c.b);
  const saturation = max > 0 ? (max - min) / max : 0;
  const brightness = (c.r + c.g + c.b) / 3;
  // 太暗或太亮的灰色得分低
  const brightnessScore = brightness > 20 && brightness < 240 ? 1 : 0.3;
  return saturation * 2 + brightnessScore;
}

/**
 * 合并重叠的重复mesh为单个多材质mesh
 * 
 * 算法：
 * 1. 按顶点数分组（同顶点数且几何相同的视为重叠副本）
 * 2. 每组只有一个材质 → 直接保留第一个
 * 3. 每组有多个材质 → 通过采样各材质的diffuse贴图，将每个三角形分配到"最有内容"的材质
 * 4. 构建geometry groups，合并为单个mesh
 */
function mergeOverlappingMeshes(object) {
  const meshes = [];
  object.traverse((child) => {
    if (child.isMesh) meshes.push(child);
  });

  // 按顶点数分组
  const groups = new Map();
  meshes.forEach((mesh) => {
    const vertCount = mesh.geometry.attributes.position?.count || 0;
    if (!groups.has(vertCount)) groups.set(vertCount, []);
    groups.get(vertCount).push(mesh);
  });

  let mergedCount = 0;
  let removedCount = 0;

  groups.forEach((groupMeshes, vertCount) => {
    if (groupMeshes.length <= 1) return;

    // 验证几何相同（比较前10个顶点）
    const refPos = groupMeshes[0].geometry.attributes.position;
    const allSame = groupMeshes.every((m) => {
      const pos = m.geometry.attributes.position;
      if (!pos || pos.count !== refPos.count) return false;
      for (let i = 0; i < Math.min(30, refPos.count * 3); i++) {
        if (Math.abs(pos.array[i] - refPos.array[i]) > 0.001) return false;
      }
      return true;
    });

    if (!allSame) {
      console.log(`  顶点数${vertCount}组：几何不完全相同，跳过合并（${groupMeshes.length}个mesh）`);
      return;
    }

    // 收集唯一材质
    const uniqueMats = [];
    const matSet = new Set();
    groupMeshes.forEach((m) => {
      const mats = Array.isArray(m.material) ? m.material : [m.material];
      mats.forEach((mat) => {
        if (mat && !matSet.has(mat.name)) {
          matSet.add(mat.name);
          uniqueMats.push(mat);
        }
      });
    });

    if (uniqueMats.length === 1) {
      // 单材质：只保留第一个，隐藏其余
      console.log(`  顶点数${vertCount}组：单材质(${uniqueMats[0].name})，保留1个，隐藏${groupMeshes.length - 1}个重复`);
      for (let i = 1; i < groupMeshes.length; i++) {
        groupMeshes[i].visible = false;
        removedCount++;
      }
      return;
    }

    // 多材质：通过采样贴图分配三角形
    console.log(`  顶点数${vertCount}组：多材质(${uniqueMats.map(m => m.name).join(', ')})，开始合并...`);

    const refMesh = groupMeshes[0];
    const refGeo = refMesh.geometry;
    const posAttr = refGeo.attributes.position;
    const uvAttr = refGeo.attributes.uv;
    const triCount = posAttr.count / 3;

    // 为每个材质创建贴图canvas
    const texCanvases = uniqueMats.map((mat) => {
      const tex = mat.map;
      if (!tex) return null;
      return textureToCanvas(tex);
    });

    // 为每个三角形分配材质索引
    const triMatIndex = new Uint16Array(triCount);
    let assigned = [0, 0, 0, 0];

    for (let t = 0; t < triCount; t++) {
      const i0 = t * 3, i1 = t * 3 + 1, i2 = t * 3 + 2;
      
      // 计算UV中心
      let cu = 0, cv = 0;
      if (uvAttr) {
        cu = (uvAttr.array[i0 * 2] + uvAttr.array[i1 * 2] + uvAttr.array[i2 * 2]) / 3;
        cv = (uvAttr.array[i0 * 2 + 1] + uvAttr.array[i1 * 2 + 1] + uvAttr.array[i2 * 2 + 1]) / 3;
      }

      // 采样每个材质的贴图，选得分最高的
      let bestMat = 0;
      let bestScore = -1;
      for (let mi = 0; mi < uniqueMats.length; mi++) {
        const c = sampleUV(texCanvases[mi], cu, cv);
        const s = colorScore(c);
        if (s > bestScore) {
          bestScore = s;
          bestMat = mi;
        }
      }
      triMatIndex[t] = bestMat;
      assigned[bestMat]++;
    }

    console.log(`    三角形分配: ${assigned.map((c, i) => `${uniqueMats[i].name}=${c}`).join(', ')}`);

    // 构建geometry groups（连续相同材质的三角形范围）
    const groups = [];
    let start = 0;
    let currentMat = triMatIndex[0];
    for (let t = 1; t <= triCount; t++) {
      if (t === triCount || triMatIndex[t] !== currentMat) {
        groups.push({ start: start * 3, count: (t - start) * 3, materialIndex: currentMat });
        start = t;
        currentMat = triMatIndex[t];
      }
    }

    // 创建合并后的mesh
    const mergedGeo = refGeo.clone();
    mergedGeo.clearGroups();
    groups.forEach((g) => mergedGeo.addGroup(g.start, g.count, g.materialIndex));

    const mergedMesh = new THREE.Mesh(mergedGeo, uniqueMats);
    mergedMesh.name = refMesh.name + '_merged';
    mergedMesh.castShadow = refMesh.castShadow;
    mergedMesh.receiveShadow = refMesh.receiveShadow;

    // 替换：在父节点中添加合并mesh，移除原mesh
    const parent = refMesh.parent;
    if (parent) {
      parent.add(mergedMesh);
      groupMeshes.forEach((m) => {
        parent.remove(m);
        m.geometry.dispose();
      });
    }

    mergedCount++;
    removedCount += groupMeshes.length;
    console.log(`    合并完成: ${groups.length}个材质组`);
  });

  console.log(`  合并结果: ${mergedCount}个mesh被合并，移除${removedCount}个原始mesh`);
}

/**
 * 将多材质mesh按geometry groups拆分为独立mesh
 * 每个子mesh只包含其组内的顶点/索引，避免GLTF导出时产生重叠副本
 */
function splitMultiMaterialMeshes(object) {
  const toProcess = [];
  object.traverse((child) => {
    if (child.isMesh && Array.isArray(child.material) && child.material.length > 1
        && child.geometry.groups && child.geometry.groups.length > 1) {
      toProcess.push(child);
    }
  });

  let splitCount = 0;
  toProcess.forEach((mesh) => {
    const geo = mesh.geometry;
    const groups = geo.groups;
    const materials = mesh.material;
    const parent = mesh.parent;

    console.log(`  拆分mesh: ${mesh.name}, ${groups.length}个groups, ${materials.length}个材质`);

    // 创建一个Group来容纳拆分后的子mesh
    const group = new THREE.Group();
    group.name = mesh.name;

    const isIndexed = !!geo.index;

    groups.forEach((g, gi) => {
      const mat = materials[g.materialIndex] || materials[0];
      let newGeo;

      if (isIndexed) {
        // 索引几何体：截取index范围
        newGeo = geo.clone();
        const indexArray = geo.index.array.slice(g.start, g.start + g.count);
        newGeo.setIndex(new THREE.BufferAttribute(indexArray, 1));
        // 重新计算顶点范围，只保留被引用的顶点（可选优化，这里先保留全部）
      } else {
        // 非索引几何体：截取顶点属性范围
        newGeo = new THREE.BufferGeometry();
        const start = g.start;
        const count = g.count;
        // 复制所有顶点属性，截取范围
        Object.keys(geo.attributes).forEach((attrName) => {
          const attr = geo.attributes[attrName];
          const itemSize = attr.itemSize;
          const sliced = attr.array.slice(start * itemSize, (start + count) * itemSize);
          newGeo.setAttribute(attrName, new THREE.BufferAttribute(sliced, itemSize));
        });
        if (geo.index) {
          newGeo.setIndex(geo.index.clone());
        }
      }

      newGeo.computeBoundingBox();
      newGeo.computeBoundingSphere();

      const newMesh = new THREE.Mesh(newGeo, mat.clone());
      newMesh.name = `${mesh.name}_part${gi}`;
      newMesh.castShadow = mesh.castShadow;
      newMesh.receiveShadow = mesh.receiveShadow;
      group.add(newMesh);
    });

    // 替换原mesh
    if (parent) {
      const idx = parent.children.indexOf(mesh);
      if (idx >= 0) {
        parent.children[idx] = group;
        group.parent = parent;
      } else {
        parent.add(group);
      }
    }
    mesh.geometry.dispose();
    splitCount++;
  });

  console.log(`  拆分结果: ${splitCount}个多材质mesh被拆分`);
}

// ==================== Main ====================
const FBX_PATH = process.argv[2] || 'D:/办公软件/内网通/nwt2194/nwt/cache/recv/罗杰/111/东汉铜车马(轺车)/fbx/SM_YaoChe.fbx';
const TEX_DIR = process.argv[3] || 'D:/办公软件/内网通/nwt2194/nwt/cache/recv/罗杰/111/东汉铜车马(轺车)/tex';
const OUT_PATH = process.argv[4] || 'G:/西秦项目管理/xiqin-model-platform/.ui-review/yaoche-fixed.glb';

console.log('加载FBX:', FBX_PATH);
const fbxBuffer = fs.readFileSync(FBX_PATH);

console.log('加载贴图目录:', TEX_DIR);
const textureFiles = [];
if (fs.existsSync(TEX_DIR)) {
  fs.readdirSync(TEX_DIR).forEach((f) => {
    if (/\.(png|jpg|jpeg|tga)$/i.test(f)) {
      textureFiles.push({
        fileName: f,
        buffer: fs.readFileSync(path.join(TEX_DIR, f)),
      });
    }
  });
}
console.log(`找到 ${textureFiles.length} 个贴图文件`);

// 加载FBX
const loader = new FBXLoader();
const object = loader.parse(fbxBuffer.buffer.slice(fbxBuffer.byteOffset, fbxBuffer.byteOffset + fbxBuffer.byteLength), '');

// 统计原始mesh
let origMeshCount = 0;
object.traverse((c) => { if (c.isMesh) origMeshCount++; });
console.log(`原始mesh数: ${origMeshCount}`);

// 应用贴图
console.log('应用贴图...');
applyTexturesToModel(object, textureFiles);

// 合并重叠mesh
console.log('合并重叠mesh...');
mergeOverlappingMeshes(object);

// 拆分多材质mesh为独立子mesh（按geometry groups），避免GLTF导出重叠副本
console.log('拆分多材质mesh...');
splitMultiMaterialMeshes(object);

// 统计合并后mesh
let finalMeshCount = 0;
let finalMatCount = 0;
object.traverse((c) => {
  if (c.isMesh) {
    finalMeshCount++;
    const mats = Array.isArray(c.material) ? c.material : [c.material];
    finalMatCount += mats.length;
    console.log(`  mesh: ${c.name}, verts=${c.geometry.attributes.position?.count}, materials=${mats.map(m => m.name).join(',')}, groups=${c.geometry.groups?.length || 0}`);
  }
});
console.log(`合并后mesh数: ${finalMeshCount}, 材质数: ${finalMatCount}`);

// 导出GLB
console.log('导出GLB...');
const exporter = new GLTFExporter();
exporter.parse(object, (result) => {
  const buf = Buffer.from(result);
  fs.writeFileSync(OUT_PATH, buf);
  console.log(`GLB已保存: ${OUT_PATH} (${(buf.length / 1024 / 1024).toFixed(2)} MB)`);
  process.exit(0);
}, (err) => {
  console.error('导出失败:', err);
  process.exit(1);
}, { binary: true });
