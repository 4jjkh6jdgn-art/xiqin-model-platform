/**
 * 格式转换模块（基于 Three.js r165）
 *
 * - convertFbxToGlb:     FBX -> GLB（使用 FBXLoader + GLTFExporter），支持嵌入外部贴图
 * - generateThumbnail:   GLB -> PNG 缩略图（headless WebGL 渲染 512x512）
 * - generatePlaceholderThumbnail: WebGL 不可用时的占位图降级方案
 */
// headless-gl 可选加载：包缺失或加载失败时缩略图自动降级为占位图，不影响 FBX->GLB 转换
let gl = null;
try {
  gl = (await import('gl')).default;
} catch (err) {
  console.warn('[converter] headless-gl 加载失败，缩略图将使用占位图:', err.message);
}
import { createCanvas, Image } from 'canvas';

// ===================== 贴图匹配与嵌入工具 =====================
const MAX_TEXTURE_SIZE = 1024; // 贴图最大边长，超过则压缩，减小GLB体积和内存占用（大模型用1024避免内存溢出）
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
  if (['AO', 'OCCLUSION', 'AMBIENT'].includes(token)) return 'ao';
  if (['E', 'EMISSIVE', 'EMISSION'].includes(token)) return 'emissive';
  if (['A', 'ALPHA', 'OPACITY'].includes(token)) return 'alpha';
  if (['DISP', 'DISPLACEMENT', 'HEIGHT'].includes(token)) return 'displacement';
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
  if (materialCore.includes(textureCore) || textureCore.includes(materialCore)) {
    return 500 + Math.min(materialCore.length, textureCore.length);
  }
  const materialText = materialCore.replace(/\d+/g, '');
  const textureText = textureCore.replace(/\d+/g, '');
  if (materialText && materialText === textureText) return 400;
  return -1;
}

/**
 * 按材质名匹配贴图通道
 * @param {string} materialName
 * @param {Array<{fileName: string, buffer: Buffer}>} textureFiles
 * @returns {Record<string, {fileName: string, buffer: Buffer}>}
 */
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

/**
 * 从 Buffer 创建 Three.js Texture（Node 环境，使用 node-canvas Image）
 */
function createTextureFromBuffer(buffer, fileName) {
  // 纹理压缩：超过最大边长则用node-canvas缩放，减小GLB体积和内存
  let imgBuffer = buffer;
  try {
    const probe = new Image();
    probe.src = buffer;
    if (probe.width > MAX_TEXTURE_SIZE || probe.height > MAX_TEXTURE_SIZE) {
      const scale = Math.min(MAX_TEXTURE_SIZE / probe.width, MAX_TEXTURE_SIZE / probe.height);
      const newW = Math.round(probe.width * scale);
      const newH = Math.round(probe.height * scale);
      const canvas = createCanvas(newW, newH);
      const ctx = canvas.getContext('2d');
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(probe, 0, 0, newW, newH);
      imgBuffer = canvas.toBuffer('image/png');
      console.log(`[converter] 纹理压缩: ${fileName} ${probe.width}x${probe.height} -> ${newW}x${newH}`);
      // 清理canvas释放内存
      ctx.clearRect(0, 0, newW, newH);
      canvas.width = 1;
      canvas.height = 1;
    }
    // 清理probe image
    probe.src = null;
  } catch (e) {
    console.warn(`[converter] 纹理压缩失败 ${fileName}:`, e.message);
  }

  const img = new Image();
  img.src = imgBuffer;
  const texture = new THREE.Texture(img);
  texture.name = fileName;
  texture.flipY = true; // FBX 源贴图沿用 FBX UV 方向
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.minFilter = THREE.LinearMipmapLinearFilter;
  texture.magFilter = THREE.LinearFilter;
  texture.generateMipmaps = true;
  const channel = getTextureChannel(fileName);
  if (channel === 'diffuse' || channel === 'emissive') {
    texture.colorSpace = THREE.SRGBColorSpace;
  } else {
    texture.colorSpace = THREE.LinearSRGBColorSpace;
  }
  return texture;
}

/**
 * 将外部贴图应用到模型材质
 * @param {THREE.Object3D} object
 * @param {Array<{fileName: string, buffer: Buffer}>} textureFiles
 */
function applyTexturesToModel(object, textureFiles) {
  if (!textureFiles || textureFiles.length === 0) return;
  let applied = 0;
  // 贴图去重缓存：同一文件只创建一次Texture，避免重复嵌入浪费内存和GLB体积
  const textureCache = new Map();
  const getCachedTexture = (file) => {
    if (!textureCache.has(file.fileName)) {
      textureCache.set(file.fileName, createTextureFromBuffer(file.buffer, file.fileName));
    }
    return textureCache.get(file.fileName);
  };
  object.traverse((child) => {
    if (!child.isMesh) return;
    const materials = Array.isArray(child.material) ? child.material : [child.material];
    materials.forEach((mat) => {
      if (!mat) return;
      const channels = matchTextureChannels(mat.name || '', textureFiles);
      if (Object.keys(channels).length === 0) return;
      try {
        if (channels.diffuse) {
          mat.map = getCachedTexture(channels.diffuse);
          if (mat.color) mat.color.set(0xffffff);
        }
        if (channels.normal) {
          mat.normalMap = getCachedTexture(channels.normal);
          // FBX 法线贴图多为 DirectX 风格，翻转 Y 轴
          if (mat.normalScale) mat.normalScale.set(1, -1);
        }
        if (channels.metalness) {
          mat.metalnessMap = getCachedTexture(channels.metalness);
          mat.metalness = 1.0;
        }
        if (channels.roughness) {
          mat.roughnessMap = getCachedTexture(channels.roughness);
          mat.roughness = 1.0;
        }
        if (channels.ao) {
          mat.aoMap = getCachedTexture(channels.ao);
        }
        if (channels.emissive) {
          mat.emissiveMap = getCachedTexture(channels.emissive);
          if (mat.emissive) mat.emissive.set(0xffffff);
        }
        mat.needsUpdate = true;
        applied++;
      } catch (err) {
        console.warn(`[converter] 材质 ${mat.name} 贴图应用失败:`, err.message);
      }
    });
  });
  if (applied > 0) {
    console.log(`[converter] 已为 ${applied} 个材质嵌入贴图（去重后 ${textureCache.size} 个独立贴图）`);
  }
}


// Three.js 的 FBXLoader/GLTFExporter 即使在 Node 环境中也会通过
// document.createElementNS 创建 canvas 或 img。使用 node-canvas 提供最小 DOM 兼容层，
// 避免模型转换依赖完整浏览器运行时。
if (typeof globalThis.document === 'undefined') {
  const createImage = () => {
    const image = new Image();
    image.addEventListener = (event, handler) => {
      if (event === 'load') {
        // node-canvas Image 的 load 事件在 width/height 设置前触发，
        // 这里轮询直到尺寸可用后再调用业务回调，保证 GLTFLoader 能读到有效图片。
        image.onload = function () {
          const check = () => {
            if (image.width > 0 && image.height > 0) {
              handler.call(image);
            } else {
              setTimeout(check, 10);
            }
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
    createElement: (name) => {
      if (name === 'canvas') return createCanvasElement();
      if (name === 'img') return createImage();
      return { style: {} };
    },
    createElementNS: (_namespace, name) => {
      if (name === 'canvas') return createCanvasElement();
      if (name === 'img') return createImage();
      return { style: {} };
    },
  };
}

// three 模块加载时会执行 `if (typeof self !== 'undefined') animation.setContext(self)`，
// Node 环境没有全局 self，导致 WebGLRenderer.dispose() 时 WebGLAnimation.stop() 崩溃。
// 必须在 import three 之前设置 globalThis.self。
if (typeof globalThis.self === 'undefined') {
  globalThis.self = {
    requestAnimationFrame: () => 0,
    cancelAnimationFrame: () => {},
    URL: globalThis.URL,
  };
}

// GLTFLoader 在 Node 中加载嵌入贴图时会调用 URL.createObjectURL(blob)。
// Node 22 自带 createObjectURL，但返回 blob:nodedata: 协议，node-canvas Image 无法识别。
// 这里通过包装 Blob 保存原始 Buffer，并覆盖 createObjectURL 返回 data URL，
// 使 GLTFLoader/TextureLoader 能正常加载嵌入贴图。
const __originalBlob = globalThis.Blob;
const __blobBufferMap = new WeakMap();
globalThis.Blob = class Blob extends __originalBlob {
  constructor(parts, options) {
    super(parts, options);
    if (parts && parts.length === 1) {
      const part = parts[0];
      if (part instanceof ArrayBuffer) {
        __blobBufferMap.set(this, Buffer.from(part));
      } else if (ArrayBuffer.isView(part)) {
        __blobBufferMap.set(this, Buffer.from(part.buffer, part.byteOffset, part.byteLength));
      }
    }
  }
};
const __originalCreateObjectURL = URL.createObjectURL?.bind(URL);
URL.createObjectURL = (blob) => {
  const buffer = __blobBufferMap.get(blob);
  if (buffer) {
    const dataUrl = `data:${blob.type || 'application/octet-stream'};base64,${buffer.toString('base64')}`;
    if (buffer.length > 1024 * 1024) {
      console.log(`[converter] createObjectURL: blob size=${(buffer.length / 1024 / 1024).toFixed(1)}MB type=${blob.type}`);
    }
    return dataUrl;
  }
  if (__originalCreateObjectURL) return __originalCreateObjectURL(blob);
  return `blob:${Date.now()}:${Math.random().toString(36).slice(2)}`;
};
URL.revokeObjectURL = () => {};

// three 相关模块统一动态导入，确保上面 self polyfill 先于 three 求值
const THREE = await import('three');
const { FBXLoader } = await import('three/examples/jsm/loaders/FBXLoader.js');
const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
const { GLTFExporter } = await import('three/examples/jsm/exporters/GLTFExporter.js');

// 部分 FBX（尤其包含嵌入贴图的二进制 FBX）会在解析阶段直接访问
// window.URL 和窗口尺寸。Three.js 模块加载完成后再补这一层，既避免改变
// Three.js 的 Node 初始化分支，也让 FBXLoader 能使用 Node 22 自带的 Blob URL。
if (typeof globalThis.window === 'undefined') {
  globalThis.window = {
    URL: globalThis.URL,
    innerWidth: 512,
    innerHeight: 512,
  };
}

/**
 * FileReader polyfill（Node 环境）
 * GLTFExporter 在 binary 模式下用 FileReader + Blob 读取二进制缓冲区，
 * Node 只有全局 Blob 而没有 FileReader，这里用 Blob.arrayBuffer() 模拟。
 */
if (typeof globalThis.FileReader === 'undefined') {
  globalThis.FileReader = class FileReader {
    constructor() {
      this.result = null;
      this.onloadend = null;
      this.onerror = null;
    }
    _emit() {
      if (this.onloadend) {
        this.onloadend({ target: this });
      }
    }
    readAsArrayBuffer(blob) {
      blob
        .arrayBuffer()
        .then((ab) => {
          this.result = ab;
          this._emit();
        })
        .catch((err) => {
          if (this.onerror) this.onerror(err);
        });
    }
    readAsDataURL(blob) {
      blob
        .arrayBuffer()
        .then((ab) => {
          const base64 = Buffer.from(ab).toString('base64');
          this.result = `data:${blob.type || 'application/octet-stream'};base64,${base64}`;
          this._emit();
        })
        .catch((err) => {
          if (this.onerror) this.onerror(err);
        });
    }
  };
}

const THUMBNAIL_SIZE = 512;
const BACKGROUND_COLOR = 0xf1f5f9; // 浅灰蓝背景
const CAMERA_FOV = 50;
const FRAME_PADDING = 1.4; // 取景留白系数

/**
 * 将 Buffer/ArrayBuffer 统一转换为 ArrayBuffer（GLTFLoader/FBXLoader 需要 ArrayBuffer）
 * @param {Buffer|ArrayBuffer} buffer
 * @returns {ArrayBuffer}
 */
function toArrayBuffer(buffer) {
  if (buffer instanceof ArrayBuffer) return buffer;
  return buffer.buffer.slice(
    buffer.byteOffset,
    buffer.byteOffset + buffer.byteLength
  );
}

/**
 * 移除未能加载的外部贴图。FBXLoader 会先返回 Texture 占位对象，即使对应图片
 * 缺失；GLTFExporter 随后会因 image 宽高为 0 而终止整个模型导出。
 */
function removeInvalidTextures(object) {
  object.traverse((node) => {
    if (!node.material) return;
    const materials = Array.isArray(node.material) ? node.material : [node.material];
    for (const material of materials) {
      for (const key of Object.keys(material)) {
        const texture = material[key];
        if (!texture?.isTexture) continue;
        const image = texture.image;
        const hasPixels = image && (
          (Number(image.width) > 0 && Number(image.height) > 0)
          || (image.data && image.data.length > 0)
        );
        if (!hasPixels) {
          material[key] = null;
          material.needsUpdate = true;
        }
      }
    }
  });
}

/**
 * 将多材质mesh按geometry groups拆分为独立子mesh
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
  toProcess.forEach((mesh) => {
    const geo = mesh.geometry;
    const groups = geo.groups;
    const materials = mesh.material;
    const parent = mesh.parent;
    console.log(`[converter] 拆分多材质mesh: ${mesh.name}, ${groups.length}个groups, ${materials.length}个材质`);
    const group = new THREE.Group();
    group.name = mesh.name;
    const isIndexed = !!geo.index;
    groups.forEach((g, gi) => {
      const mat = materials[g.materialIndex] || materials[0];
      let newGeo;
      if (isIndexed) {
        newGeo = geo.clone();
        const indexArray = geo.index.array.slice(g.start, g.start + g.count);
        newGeo.setIndex(new THREE.BufferAttribute(indexArray, 1));
      } else {
        newGeo = new THREE.BufferGeometry();
        const start = g.start;
        const count = g.count;
        Object.keys(geo.attributes).forEach((attrName) => {
          const attr = geo.attributes[attrName];
          const itemSize = attr.itemSize;
          const sliced = attr.array.slice(start * itemSize, (start + count) * itemSize);
          newGeo.setAttribute(attrName, new THREE.BufferAttribute(sliced, itemSize));
        });
      }
      newGeo.computeBoundingBox();
      newGeo.computeBoundingSphere();
      const newMesh = new THREE.Mesh(newGeo, mat.clone ? mat.clone() : mat);
      newMesh.name = `${mesh.name}_part${gi}`;
      newMesh.castShadow = mesh.castShadow;
      newMesh.receiveShadow = mesh.receiveShadow;
      group.add(newMesh);
    });
    if (parent) {
      const idx = parent.children.indexOf(mesh);
      if (idx >= 0) { parent.children[idx] = group; group.parent = parent; }
      else { parent.add(group); }
    }
    mesh.geometry.dispose();
  });
}

/**
 * 递归释放Three.js对象及其几何体、材质、贴图内存
 * 大模型转换后必须调用，避免内存占用过高导致后续操作超时
 * @param {THREE.Object3D} object
 */
function disposeObject(object) {
  if (!object) return;
  object.traverse((child) => {
    if (child.geometry) {
      child.geometry.dispose();
    }
    if (child.material) {
      const materials = Array.isArray(child.material) ? child.material : [child.material];
      materials.forEach((mat) => {
        if (!mat) return;
        ['map', 'normalMap', 'metalnessMap', 'roughnessMap', 'aoMap', 'emissiveMap', 'alphaMap', 'bumpMap', 'displacementMap', 'envMap', 'lightMap'].forEach((key) => {
          if (mat[key]) {
            mat[key].dispose();
          }
        });
        mat.dispose();
      });
    }
  });
}

/**
 * FBX Buffer -> GLB Buffer
 * @param {Buffer} buffer FBX 文件二进制内容
 * @param {Array<{fileName: string, buffer: Buffer}>} [textureFiles] 外部贴图文件，将按材质名匹配并嵌入 GLB
 * @returns {Promise<Buffer>} GLB 文件二进制内容
 */
export function convertFbxToGlb(buffer, textureFiles = []) {
  return new Promise((resolve, reject) => {
    try {
      const manager = new THREE.LoadingManager();
      // 外部贴图资源（如相对路径引用的贴图）在无服务器环境下加载失败时仅告警，不影响几何转换
      manager.onError = (url) => {
        console.warn(`[converter] 资源加载失败（跳过）: ${url}`);
      };

      const loader = new FBXLoader(manager);

      // 注意：three r165 的 FBXLoader.parse(FBXBuffer, path) 是【同步】方法，直接返回 Object3D，
      // 不接受 onLoad/onError 回调（额外参数会被静默忽略导致 Promise 永不 resolve）。
      // 必须用同步返回值，再用 GLTFExporter 异步导出 GLB。
      const object = loader.parse(toArrayBuffer(buffer), '');
      removeInvalidTextures(object);

      // 嵌入外部贴图，使 GLB 自包含，缩略图与预览均可直接显示材质
      if (textureFiles.length > 0) {
        applyTexturesToModel(object, textureFiles);
      }

      // 拆分多材质mesh为独立子mesh，避免GLTF导出重叠副本
      splitMultiMaterialMeshes(object);

      const exporter = new GLTFExporter();
      exporter.parse(
        object,
        (result) => {
          // binary: true 时 result 为 ArrayBuffer
          const glbBuffer = Buffer.from(result);
          // 导出完成后立即释放Three.js对象内存，避免大模型占用过高导致后续上传超时
          disposeObject(object);
          resolve(glbBuffer);
        },
        (error) => {
          disposeObject(object);
          reject(new Error(`GLTF 导出失败: ${error?.message || error}`));
        },
        { binary: true }
      );
    } catch (error) {
      reject(new Error(`FBX 解析失败: ${error?.message || error}`));
    }
  });
}

/**
 * 创建 headless WebGL 渲染器
 * @returns {{ renderer: THREE.WebGLRenderer, glContext: WebGLRenderingContext, dispose: () => void }}
 */
function createHeadlessRenderer() {
  if (!gl) {
    throw new Error('headless-gl 不可用（未加载成功）');
  }
  const glContext = gl(THUMBNAIL_SIZE, THUMBNAIL_SIZE, {
    preserveDrawingBuffer: true,
    antialias: true,
    alpha: false,
    depth: true,
    stencil: false,
  });

  if (!glContext) {
    throw new Error('创建 headless WebGL 上下文失败');
  }

  // three r165 的 WebGLRenderer 初始化时会为 TEXTURE_3D / TEXTURE_2D_ARRAY 创建空纹理，
  // 直接调用 gl.texImage3D()，而 headless-gl 只实现 WebGL1 API。
  // 这些空纹理不会被实际渲染使用，这里补一个 no-op 以避免初始化崩溃。
  if (typeof glContext.texImage3D !== 'function') {
    glContext.texImage3D = () => {};
  }

  // WebGLRenderer.dispose() 会调用 glContext.cancelAnimationFrame()（WebGLAnimation），
  // headless-gl 的上下文没有该 API，补 no-op 避免 dispose 崩溃。
  if (typeof glContext.cancelAnimationFrame !== 'function') {
    glContext.cancelAnimationFrame = () => {};
  }

  // Three.js WebGLRenderer 需要一个类 DOM canvas 对象
  const fakeCanvas = {
    width: THUMBNAIL_SIZE,
    height: THUMBNAIL_SIZE,
    clientWidth: THUMBNAIL_SIZE,
    clientHeight: THUMBNAIL_SIZE,
    style: {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
    cancelAnimationFrame: () => {},
    getContext: () => glContext,
  };

  const renderer = new THREE.WebGLRenderer({
    canvas: fakeCanvas,
    context: glContext,
    antialias: true,
    alpha: false,
  });

  renderer.setSize(THUMBNAIL_SIZE, THUMBNAIL_SIZE, false);
  renderer.setClearColor(BACKGROUND_COLOR, 1);
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  return {
    renderer,
    glContext,
    dispose: () => renderer.dispose(),
  };
}

/**
 * 搭建带基础照明的场景
 */
function buildScene() {
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(BACKGROUND_COLOR);

  const ambient = new THREE.AmbientLight(0xffffff, 0.6);
  scene.add(ambient);

  const keyLight = new THREE.DirectionalLight(0xffffff, 0.9);
  keyLight.position.set(5, 10, 7);
  scene.add(keyLight);

  const fillLight = new THREE.DirectionalLight(0xffffff, 0.35);
  fillLight.position.set(-5, -3, -5);
  scene.add(fillLight);

  const rimLight = new THREE.DirectionalLight(0xffffff, 0.2);
  rimLight.position.set(0, -5, 5);
  scene.add(rimLight);

  return scene;
}

/**
 * 将模型居中并缩放到适配相机取景
 * @param {THREE.Object3D} model
 * @returns {THREE.PerspectiveCamera}
 */
function frameModel(model) {
  // 计算原始包围盒
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());

  const maxDim = Math.max(size.x, size.y, size.z) || 1;
  const scale = 2 / maxDim;

  model.scale.setScalar(scale);
  model.position.set(-center.x * scale, -center.y * scale, -center.z * scale);

  // 缩放后的包围盒尺寸（用于相机距离）
  const fittedBox = new THREE.Box3().setFromObject(model);
  const fittedSize = fittedBox.getSize(new THREE.Vector3());
  const fittedMaxDim = Math.max(fittedSize.x, fittedSize.y, fittedSize.z) || 1;

  const halfFov = (CAMERA_FOV * Math.PI) / 180 / 2;
  const distance = (fittedMaxDim / 2 / Math.tan(halfFov)) * FRAME_PADDING;

  const camera = new THREE.PerspectiveCamera(CAMERA_FOV, 1, 0.1, 1000);
  camera.position.set(distance, distance * 0.7, distance);
  camera.lookAt(0, 0, 0);

  return camera;
}

/**
 * 生成占位缩略图（WebGL 不可用或渲染失败时的降级方案）
 * 使用 node-canvas 绘制 512x512 占位图，保证处理流水线不中断
 * @param {string} title 模型名称（可选）
 * @returns {Buffer} PNG 图片二进制内容
 */
export function generatePlaceholderThumbnail(title = '') {
  const size = THUMBNAIL_SIZE;
  const canvas2d = createCanvas(size, size);
  const ctx = canvas2d.getContext('2d');

  // 渐变背景
  const gradient = ctx.createLinearGradient(0, 0, size, size);
  gradient.addColorStop(0, '#e2e8f0');
  gradient.addColorStop(1, '#cbd5e1');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, size, size);

  // 简单立方体线框示意
  ctx.strokeStyle = '#64748b';
  ctx.lineWidth = 6;
  ctx.strokeRect(size * 0.3, size * 0.28, size * 0.4, size * 0.4);

  // 标题文字
  const label = title && title.length > 20 ? title.slice(0, 20) + '…' : (title || '3D MODEL');
  ctx.fillStyle = '#334155';
  ctx.font = 'bold 28px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, size / 2, size * 0.8);

  return canvas2d.toBuffer('image/png');
}

/**
 * 从 GLB Buffer 生成 512x512 PNG 缩略图
 * @param {Buffer} gltfBuffer GLB 文件二进制内容
 * @returns {Promise<Buffer>} PNG 图片二进制内容
 */
export function generateThumbnail(gltfBuffer) {
  return new Promise((resolve, reject) => {
    let headless = null;
    const arrayBuffer = toArrayBuffer(gltfBuffer);

    try {
      headless = createHeadlessRenderer();
    } catch (err) {
      reject(err);
      return;
    }

    const { renderer, glContext } = headless;
    const scene = buildScene();
    const camera = new THREE.PerspectiveCamera(CAMERA_FOV, 1, 0.1, 1000);

    const loader = new GLTFLoader();

    loader.parse(
      arrayBuffer,
      '',
      (gltf) => {
        try {
          const model = gltf.scene;

          // 兼容 GLTF 中可能存在的轴心修正（如 +Y up）
          model.updateMatrixWorld(true);

          // headless-gl 的 texImage2D 只接受 ImageData/HTMLCanvasElement 等类型，
          // 不接受 node-canvas Image。将嵌入贴图的 image 转换为 canvas 以便渲染。
          model.traverse((child) => {
            if (!child.isMesh) return;
            const materials = Array.isArray(child.material) ? child.material : [child.material];
            materials.forEach((mat) => {
              if (!mat) return;
              for (const key of Object.keys(mat)) {
                const tex = mat[key];
                if (!tex || !tex.isTexture || !tex.image) continue;
                const img = tex.image;
                const typeName = img?.constructor?.name || typeof img;
                console.log(`[converter] 纹理 ${key}: image type=${typeName} width=${img?.width} height=${img?.height}`);
                if (img && typeof img === 'object' && (typeName === 'Image' || typeName === 'HTMLImageElement')) {
                  try {
                    const canvas = createCanvas(img.width, img.height);
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0);
                    tex.image = canvas;
                    tex.needsUpdate = true;
                    console.log(`[converter] 已将 ${key} 的 image 转为 canvas`);
                  } catch (e) {
                    console.warn('[converter] 贴图转 canvas 失败:', e.message);
                  }
                }
              }
            });
          });

          const framedCamera = frameModel(model);
          Object.assign(camera.position, framedCamera.position);
          camera.lookAt(0, 0, 0);

          scene.add(model);

          renderer.render(scene, camera);

          // 从 WebGL 上下文读取像素
          const pixels = new Uint8Array(THUMBNAIL_SIZE * THUMBNAIL_SIZE * 4);
          glContext.readPixels(
            0,
            0,
            THUMBNAIL_SIZE,
            THUMBNAIL_SIZE,
            glContext.RGBA,
            glContext.UNSIGNED_BYTE,
            pixels
          );

          // WebGL 原点在左下角，垂直翻转后写入 2D canvas 编码为 PNG
          const canvas2d = createCanvas(THUMBNAIL_SIZE, THUMBNAIL_SIZE);
          const ctx2d = canvas2d.getContext('2d');
          const imageData = ctx2d.createImageData(THUMBNAIL_SIZE, THUMBNAIL_SIZE);

          for (let y = 0; y < THUMBNAIL_SIZE; y++) {
            const srcOffset = (THUMBNAIL_SIZE - 1 - y) * THUMBNAIL_SIZE * 4;
            const dstOffset = y * THUMBNAIL_SIZE * 4;
            imageData.data.set(
              pixels.subarray(srcOffset, srcOffset + THUMBNAIL_SIZE * 4),
              dstOffset
            );
          }

          ctx2d.putImageData(imageData, 0, 0);
          const pngBuffer = canvas2d.toBuffer('image/png');

          headless.dispose();
          resolve(pngBuffer);
        } catch (err) {
          headless.dispose();
          reject(new Error(`缩略图渲染失败: ${err?.message || err}`));
        }
      },
      (error) => {
        headless.dispose();
        reject(new Error(`GLB 解析失败: ${error?.message || error}`));
      }
    );
  });
}
