<template>
  <div
    ref="containerRef" class="viewer-container"
    :data-model-state="error ? 'error' : loading ? 'loading' : modelInfo ? 'ready' : 'idle'"
    :data-model-info="modelInfo ? JSON.stringify(modelInfo) : ''"
    :data-grid-visible="String(showGrid)"
  >
    <div class="viewer-toolbar">
      <div v-if="$slots['toolbar-actions']" class="viewer-toolbar-left">
        <slot name="toolbar-actions" />
      </div>
      <div class="viewer-toolbar-controls">
        <div class="viewer-toolbar-center">
          <el-button
            class="viewer-tool-button" size="small" circle title="线框模式"
            :class="{ 'is-active': wireframe }" :aria-pressed="wireframe" @click="toggleWireframe"
          >
            <el-icon><Grid /></el-icon>
          </el-button>
          <el-button
            class="viewer-tool-button" size="small" circle title="自动旋转"
            :class="{ 'is-active': autoRotate }" :aria-pressed="autoRotate" @click="toggleAutoRotate"
          >
            <el-icon><VideoPlay /></el-icon>
          </el-button>
          <el-button
            class="viewer-tool-button" size="small" circle title="灯光设置"
            :class="{ 'is-active': showLighting }" :aria-pressed="showLighting"
            @click="showLighting = !showLighting; statsPinned = false"
          >
            <el-icon><Sunny /></el-icon>
          </el-button>
        </div>
        <div class="viewer-toolbar-right">
          <el-button
            class="viewer-tool-button" size="small" circle title="模型信息"
            :class="{ 'is-active': statsPinned }" :aria-pressed="statsPinned"
            @mouseenter="statsHovered = true" @mouseleave="statsHovered = false" @click="statsPinned = !statsPinned; showLighting = false"
          >
            <el-icon><InfoFilled /></el-icon>
          </el-button>
          <el-button class="viewer-tool-button" size="small" circle @click="screenshot" title="截图">
            <el-icon><Camera /></el-icon>
          </el-button>
          <el-button
            class="viewer-tool-button" size="small" circle
            :class="{ 'is-active': isFullscreen }" :aria-pressed="isFullscreen"
            @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏预览'"
          >
            <el-icon><FullScreen /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- Lighting control panel -->
    <div v-if="showLighting" class="lighting-panel">
      <div class="lp-header">
        <div><strong>灯光与环境</strong><small>实时预览，保存后作为该模型默认效果</small></div>
        <button type="button" @click="showLighting = false">×</button>
      </div>
      <div class="lp-presets">
        <button v-for="preset in lightingPresets" :key="preset.name" type="button" @click="applyLightingPreset(preset.values)">{{ preset.name }}</button>
      </div>
      <div class="lp-row">
        <span><i class="light-dot ambient"></i>环境光</span>
        <el-slider v-model="lighting.ambient" :min="0" :max="1.5" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.ambient.toFixed(2) }}</b>
      </div>
      <div class="lp-row">
        <span><i class="light-dot key"></i>主光</span>
        <el-slider v-model="lighting.directional" :min="0" :max="2.5" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.directional.toFixed(2) }}</b>
      </div>
      <div class="lp-row">
        <span><i class="light-dot fill"></i>补光</span>
        <el-slider v-model="lighting.fill" :min="0" :max="1.5" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.fill.toFixed(2) }}</b>
      </div>
      <div class="lp-row">
        <span><i class="light-dot rim"></i>轮廓光</span>
        <el-slider v-model="lighting.rim" :min="0" :max="1.5" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.rim.toFixed(2) }}</b>
      </div>
      <div class="lp-row">
        <span><i class="light-dot hemi"></i>天空光</span>
        <el-slider v-model="lighting.hemisphere" :min="0" :max="1.2" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.hemisphere.toFixed(2) }}</b>
      </div>
      <div class="lp-row">
        <span>曝光</span>
        <el-slider v-model="lighting.exposure" :min="0.5" :max="1.8" :step="0.05" @input="applyLighting" />
        <b>{{ lighting.exposure.toFixed(2) }}</b>
      </div>
      <div class="lp-color-row">
        <span>背景色</span>
        <el-color-picker v-model="lighting.background" @change="applyLighting" />
      </div>
      <div class="lp-actions">
        <el-button size="small" @click="resetLighting">恢复默认</el-button>
        <el-button v-permission="'model:scene_manage'" size="small" type="primary" @click="emitLighting">保存灯光</el-button>
      </div>
    </div>

    <div v-if="loading" class="viewer-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <span>加载模型中...</span>
    </div>
    <div v-if="error" class="viewer-error">
      <el-icon :size="32"><WarningFilled /></el-icon>
      <span>{{ error }}</span>
    </div>
    <div v-if="modelInfo && !showLighting && (statsHovered || statsPinned)" class="viewer-info">
      <div><span>顶点</span><strong>{{ modelInfo.vertices.toLocaleString() }}</strong></div>
      <div><span>面片</span><strong>{{ modelInfo.triangles.toLocaleString() }}</strong></div>
      <div><span>材质</span><strong>{{ modelInfo.materials }}</strong></div>
      <div><span>贴图</span><strong>{{ modelInfo.textures }}</strong></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { FBXLoader } from 'three/examples/jsm/loaders/FBXLoader.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader.js'
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader.js'
import { bestSingleMaterialChannels, getTextureChannel, matchTextureChannels } from '@/utils/modelFileRules'

const props = defineProps<{
  modelUrl: string
  textureFiles?: { url: string; fileName: string }[]
  textureFlipY?: boolean
  format?: string
  initialCamera?: string | null   // JSON: {position:[x,y,z], target:[x,y,z]}
  initialLighting?: string | null  // JSON: {ambient, directional, background}
}>()

const emit = defineEmits(['loaded', 'lighting-change'])

const containerRef = ref<HTMLDivElement>()
const loading = ref(false)
const error = ref('')
const autoRotate = ref(false)
const showGrid = ref(false)
const isFullscreen = ref(false)
const showLighting = ref(false)
const statsHovered = ref(false)
const statsPinned = ref(false)
const wireframe = ref(false)
const modelInfo = ref<{ vertices: number, triangles: number, materials: number, textures: number } | null>(null)

const defaultLighting = {
  ambient: 0.45,
  directional: 1.15,
  fill: 0.45,
  rim: 0.55,
  hemisphere: 0.35,
  exposure: 1.0,
  background: '#0b1220'
}
// 针对青铜器/深色金属 PBR 模型的自动提亮预设：提升环境光与主光，让暗色贴图细节可见
const metallicBoostPreset = {
  ambient: 0.7,
  directional: 1.55,
  fill: 0.6,
  rim: 0.85,
  hemisphere: 0.55,
  exposure: 1.3,
  background: '#141c2b'
}
const lighting = reactive({ ...defaultLighting })
const lightingPresets = [
  { name: '标准', values: defaultLighting },
  { name: '柔和', values: { ambient: 0.7, directional: 0.85, fill: 0.55, rim: 0.25, hemisphere: 0.5, exposure: 1.05 } },
  { name: '展陈', values: { ambient: 0.3, directional: 1.35, fill: 0.35, rim: 0.8, hemisphere: 0.3, exposure: 1.1 } },
  { name: '轮廓', values: { ambient: 0.2, directional: 0.8, fill: 0.2, rim: 1.2, hemisphere: 0.2, exposure: 1.0 } }
]

const savedInitialCamera = ref<{ position: number[]; target: number[] } | null>(null)

let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer
let controls: OrbitControls
let currentModel: THREE.Object3D | null = null
let gridHelper: THREE.GridHelper
let animationId: number
let autoFitCamera = true
let ambientLight: THREE.AmbientLight
let dirLight: THREE.DirectionalLight
let fillLight: THREE.DirectionalLight
let rimLight: THREE.DirectionalLight
let hemiLight: THREE.HemisphereLight
let materialCount = 0
let textureAssignIndex = 0

const init = () => {
  const container = containerRef.value
  if (!container) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(lighting.background)
  // 调试：暴露场景到全局，便于检查材质纹理
  ;(window as any).__viewerScene = scene

  camera = new THREE.PerspectiveCamera(60, container.clientWidth / container.clientHeight, 0.1, 10000)
  camera.position.set(5, 5, 5)

  renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true })
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.setPixelRatio(window.devicePixelRatio)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.0
  container.appendChild(renderer.domElement)

  // 环境贴图：为 PBR 材质提供柔和反射，避免金属/粗糙材质在缺环境时死黑
  const pmrem = new THREE.PMREMGenerator(renderer)
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.05
  controls.autoRotate = false
  controls.autoRotateSpeed = 2.0
  controls.addEventListener('start', () => { autoFitCamera = false })

  ambientLight = new THREE.AmbientLight(0xffffff, lighting.ambient)
  scene.add(ambientLight)

  dirLight = new THREE.DirectionalLight(0xffffff, lighting.directional)
  dirLight.position.set(10, 10, 10)
  dirLight.castShadow = true
  dirLight.shadow.mapSize.width = 2048
  dirLight.shadow.mapSize.height = 2048
  scene.add(dirLight)

  fillLight = new THREE.DirectionalLight(0xbfd7ff, lighting.fill)
  fillLight.position.set(-10, 6, 6)
  scene.add(fillLight)

  rimLight = new THREE.DirectionalLight(0x8fb9ff, lighting.rim)
  rimLight.position.set(2, 8, -10)
  scene.add(rimLight)

  hemiLight = new THREE.HemisphereLight(0xdbeafe, 0x111827, lighting.hemisphere)
  scene.add(hemiLight)

  gridHelper = new THREE.GridHelper(20, 20, 0x3b4b68, 0x1d2a3e)
  gridHelper.visible = showGrid.value
  scene.add(gridHelper)

  const animate = () => {
    animationId = requestAnimationFrame(animate)
    controls.update()
    renderer.render(scene, camera)
  }
  animate()

  const resizeObserver = new ResizeObserver(() => {
    if (!container) return
    camera.aspect = container.clientWidth / container.clientHeight
    camera.updateProjectionMatrix()
    renderer.setSize(container.clientWidth, container.clientHeight)
    if (currentModel && autoFitCamera) resetCamera()
  })
  resizeObserver.observe(container)
}

const loadModel = async (url: string, format?: string) => {
  if (!scene || !url) return
  loading.value = true
  error.value = ''
  autoFitCamera = true

  if (currentModel) {
    scene.remove(currentModel)
    disposeModel(currentModel)
    currentModel = null
  }

  try {
    const formatLower = (format || url.split('.').pop() || '').toLowerCase()
    let object: THREE.Object3D

    if (['fbx'].includes(formatLower)) {
      const loader = new FBXLoader()
      object = await loader.loadAsync(url)
    } else if (['glb', 'gltf'].includes(formatLower)) {
      const loader = new GLTFLoader()
      const gltf = await loader.loadAsync(url)
      object = gltf.scene
    } else if (['obj'].includes(formatLower)) {
      const loader = new OBJLoader()
      object = await loader.loadAsync(url)
    } else if (['stl'].includes(formatLower)) {
      const loader = new STLLoader()
      const geometry = await loader.loadAsync(url)
      const material = new THREE.MeshStandardMaterial({ color: 0x8899aa, metalness: 0.1, roughness: 0.7 })
      object = new THREE.Mesh(geometry, material)
    } else {
      const loader = new GLTFLoader()
      const gltf = await loader.loadAsync(url)
      object = gltf.scene
    }

    // 统计实际材质实例数量（含无名字材质），用于单/多材质判定与贴图兜底
    const materialSet = new Set<THREE.Material>()
    object.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        const mats = Array.isArray(child.material) ? child.material : [child.material]
        mats.forEach((m: any) => { if (m) materialSet.add(m) })
      }
    })
    materialCount = materialSet.size || 1

    const meshes: THREE.Mesh[] = []
    object.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.castShadow = true
        child.receiveShadow = true
        meshes.push(child)
      }
    })
    // 调试：立即记录UV范围和材质名
    ;(window as any).__loadModelRan = true
    ;(window as any).__meshCount = meshes.length
    try {
      const uvRanges: any[] = []
      // 按顶点数分组，隐藏重复mesh。
      // 关键：同父亲的子mesh是GLTF多材质primitives（共享几何体、不同材质/索引），必须全部保留；
      // 只有不同父亲且同顶点数的mesh才视为真正的重叠副本，隐藏避免z-fighting。
      const seenVerts = new Map<number, Set<string>>() // vertCount -> Set<parentUuid>
      let hiddenDup = 0
      meshes.forEach((mesh) => {
        if (!mesh.geometry) return
        const vertCount = mesh.geometry.attributes.position?.count || 0
        const parentId = mesh.parent?.uuid || 'root'
        let parentSet = seenVerts.get(vertCount)
        if (!parentSet) {
          parentSet = new Set()
          seenVerts.set(vertCount, parentSet)
        }
        if (parentSet.has(parentId)) {
          // 同父亲的GLTF primitive，保留可见
        } else if (parentSet.size > 0) {
          // 不同父亲且已有其他组，视为重叠副本，隐藏
          mesh.visible = false
          hiddenDup++
        } else {
          // 该顶点数的第一个mesh，记录父亲
          parentSet.add(parentId)
        }
        const uv = mesh.geometry.attributes.uv
        const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        const matNames = mats.map((m: any) => m?.name || 'unnamed')
        if (uv) {
          let uMin=Infinity, uMax=-Infinity, vMin=Infinity, vMax=-Infinity
          for (let i = 0; i < uv.count; i++) {
            const u = uv.array[i * 2], v = uv.array[i * 2 + 1]
            if(u<uMin)uMin=u; if(u>uMax)uMax=u
            if(v<vMin)vMin=v; if(v>vMax)vMax=v
          }
          uvRanges.push({name: mesh.name, visible: mesh.visible, mat: matNames.join(','), uMin: uMin.toFixed(3), uMax: uMax.toFixed(3), vMin: vMin.toFixed(3), vMax: vMax.toFixed(3), count: uv.count})
        } else {
          uvRanges.push({name: mesh.name, visible: mesh.visible, mat: matNames.join(','), noUV: true})
        }
      })
      ;(window as any).__uvRanges = uvRanges
      ;(window as any).__hiddenDuplicates = hiddenDup
      console.log(`[ModelViewer] 隐藏了 ${hiddenDup} 个重复mesh`)
    } catch (e) {
      ;(window as any).__uvDebugError = String(e)
    }
    // 注意：不再做材质统一修复。同一mesh组中不同材质对应不同贴图组（如M_YaoChe_02车轮、M_YaoChe_04车厢），
    // 统一材质会导致贴图错误。如需修复材质分配问题，应在模型源文件中修正，而非运行时强制合并。
    // UV翻转修复：对材质名以数字结尾的模型（如 M_YaoChe_01）翻转UV的V坐标
    try {
      const hasNumericMaterial = meshes.some((mesh) => {
        const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        return mats.some((m: any) => m && m.name && /_\d+$/.test(m.name))
      })
      if (hasNumericMaterial) {
        // 调试：记录UV范围
        const uvRanges: any[] = []
        meshes.forEach((mesh) => {
          if (!mesh.visible || !mesh.geometry) return
          const uv = mesh.geometry.attributes.uv
          if (uv) {
            let uMin=Infinity, uMax=-Infinity, vMin=Infinity, vMax=-Infinity
            for (let i = 0; i < uv.count; i++) {
              const u = uv.array[i * 2], v = uv.array[i * 2 + 1]
              if(u<uMin)uMin=u; if(u>uMax)uMax=u
              if(v<vMin)vMin=v; if(v>vMax)vMax=v
            }
            uvRanges.push({name: mesh.name, uMin: uMin.toFixed(3), uMax: uMax.toFixed(3), vMin: vMin.toFixed(3), vMax: vMax.toFixed(3), count: uv.count})
          }
        })
        ;(window as any).__uvRanges = uvRanges
        console.log('[ModelViewer] UV范围:', JSON.stringify(uvRanges, null, 2))
      }
    } catch (e) {
      console.warn('[ModelViewer] UV调试失败:', e)
    }
    if (props.textureFiles && props.textureFiles.length > 0) {
      textureAssignIndex = 0
      ;(window as any).__textureDebug = []
      await Promise.all(meshes.map((mesh) => applyTextures(mesh, props.textureFiles!, true)))

      // 若模型上传了金属度贴图且未保存过灯光，自动提亮，避免青铜器/深色金属 PBR 模型死黑
      const hasMetalnessTexture = props.textureFiles.some((f) => getTextureChannel(f.fileName) === 'metalness')
      if (hasMetalnessTexture && !props.initialLighting) {
        applyLightingPreset(metallicBoostPreset)
        console.log('[ModelViewer] 检测到金属度贴图，已自动应用金属文物提亮灯光')
      }
    }

    const box = new THREE.Box3().setFromObject(object)
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    const scale = 10 / maxDim
    object.scale.setScalar(scale)
    object.position.sub(center.multiplyScalar(scale))

    const newBox = new THREE.Box3().setFromObject(object)
    object.position.y -= newBox.min.y

    currentModel = object
    scene.add(object)

    // 修复纹理包裹模式：FBX→GLB 转换可能丢失 wrap 设置，默认 ClampToEdge 会导致 UV 超出 0-1 时纹理被拉伸成条纹
    fixTextureWrapping(object)

    // 修复背面剔除导致的面消失：某些 FBX/OBJ 模型法线方向反了，特定角度下背面被剔除，露出背景或内部
    // 统一设置为双面渲染，确保模型从任何角度看都完整
    object.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        const mats = Array.isArray(child.material) ? child.material : [child.material]
        mats.forEach((mat: any) => {
          if (mat) {
            mat.side = THREE.DoubleSide
            mat.needsUpdate = true
          }
        })
      }
    })

    let vertices = 0
    let triangles = 0
    let materials = new Set<string>()
    let textures = new Set<string>()
    const textureProps = ['map', 'normalMap', 'roughnessMap', 'metalnessMap', 'aoMap', 'emissiveMap', 'alphaMap', 'displacementMap', 'lightMap', 'bumpMap']
    object.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        const geometry = child.geometry
        if (geometry.attributes.position) {
          vertices += geometry.attributes.position.count
        }
        if (geometry.index) {
          triangles += geometry.index.count / 3
        } else if (geometry.attributes.position) {
          triangles += geometry.attributes.position.count / 3
        }
        const mesh = child as THREE.Mesh
        const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        mats.forEach(m => {
          if (!m) return
          materials.add(m.uuid)
          textureProps.forEach(prop => {
            const tex = (m as any)[prop]
            if (tex && tex.uuid) textures.add(tex.uuid)
          })
        })
      }
    })
    // 如果用户上传了贴图，异步加载完成后数量应以实际可用为准；这里优先显示用户贴图数量，避免显示 0
    const userTextureCount = props.textureFiles?.length || 0
    modelInfo.value = {
      vertices,
      triangles,
      materials: materials.size,
      textures: userTextureCount > 0 ? userTextureCount : textures.size
    }

    // Apply saved initial camera if present, else default reset
    if (savedInitialCamera.value && isCameraStateUsable(savedInitialCamera.value)) {
      applyCameraState(savedInitialCamera.value)
      autoFitCamera = false
    } else {
      resetCamera()
    }
    emit('loaded', modelInfo.value)
  } catch (e: any) {
    console.error('Failed to load model:', e)
    error.value = `模型加载失败: ${e.message || '未知错误'}`
  } finally {
    loading.value = false
  }
}

interface TextureChannels {
  diffuse?: string
  normal?: string
  roughness?: string
  metalness?: string
  ao?: string
  emissive?: string
  alpha?: string
  displacement?: string
}

// 贴图文件结构：携带 url 与 fileName（URL 本身不含文件名，必须靠 fileName 匹配）
interface TextureFile { url: string; fileName: string }

// 描述性主贴图：不以 T_ 开头、且无法解析出通道后缀，通常是真正的 albedo/color
const extractDescriptiveFiles = (files: TextureFile[]): TextureFile[] => {
  return files.filter(f => {
    const base = (f.fileName || '').replace(/\.[^.]+$/, '')
    if (/^T_/i.test(base)) return false
    return !getTextureChannel(base)
  })
}

const addCacheBuster = (url: string) => {
  if (!url) return url
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}_t=${Date.now()}`
}

// 统一将所有纹理设置为 RepeatWrapping，避免 UV 超出 0-1 时纹理被钳制拉伸成条纹
// 动态遍历材质所有属性，覆盖 MeshStandardMaterial/MeshPhysicalMaterial 的全部贴图通道
const fixTextureWrapping = (object: THREE.Object3D) => {
  object.traverse((child) => {
    if (!(child instanceof THREE.Mesh)) return
    const mats = Array.isArray(child.material) ? child.material : [child.material]
    mats.forEach((mat: any) => {
      if (!mat) return
      // 遍历材质所有可枚举属性，找到 Texture 类型并设置包裹模式
      for (const key of Object.keys(mat)) {
        const tex = mat[key]
        if (tex && tex.isTexture) {
          tex.wrapS = THREE.RepeatWrapping
          tex.wrapT = THREE.RepeatWrapping
          tex.minFilter = THREE.LinearFilter
          tex.magFilter = THREE.LinearFilter
          tex.generateMipmaps = false
          tex.needsUpdate = true
        }
      }
    })
  })
}

const applyMap = (material: any, prop: string, url: string): Promise<boolean> => {
  if (!material || !(prop in material)) return Promise.resolve(false)
  const loader = new THREE.TextureLoader()
  return new Promise((resolve) => {
    loader.load(addCacheBuster(url), (texture) => {
      texture.flipY = props.textureFlipY ?? !/^(glb|gltf)$/i.test(props.format || '')
      texture.wrapS = THREE.RepeatWrapping
      texture.wrapT = THREE.RepeatWrapping
      texture.minFilter = THREE.LinearFilter
      texture.magFilter = THREE.LinearFilter
      texture.generateMipmaps = false
      // color space: sRGB for visible color data, linear for data textures
      if (prop === 'map' || prop === 'emissiveMap') {
        texture.colorSpace = THREE.SRGBColorSpace
      } else {
        texture.colorSpace = THREE.LinearSRGBColorSpace
      }
      const old = material[prop]
      material[prop] = texture
      material.needsUpdate = true
      if (old && old !== texture && typeof old.dispose === 'function') {
        old.dispose()
      }
      if (prop === 'map' && material.color) {
        material.color.set(0xffffff)
      }
      resolve(true)
    }, undefined, (err: any) => {
      console.error(`Texture load failed: ${url} (${prop})`, err)
      resolve(false)
    })
  })
}

// 按纹理名称中的数字后缀排序，返回有序的纹理组（用于多材质模型名称匹配失败时的顺序兜底）
const getOrderedTextureGroups = (files: TextureFile[]): Array<{ core: string; channels: TextureChannels }> => {
  const groups = new Map<string, TextureFile[]>()
  files.forEach(file => {
    if (!getTextureChannel(file.fileName) || !file.url) return
    const base = file.fileName.replace(/\.[^.]+$/, '').replace(/^(SM|SK|T|M|MI|MAT|MTL|TEX)[ _.-]+/i, '').replace(/(?:^|[_ .-])(BASE[ _-]?COLOR|BASECOLOR|DIFFUSE|ALBEDO|COLOR|NORMAL|NORMALGL|NORMALDX|BUMP|METALLIC|METALNESS|METAL|ROUGHNESS|ROUGH|AO|OCCLUSION|AMBIENT|EMISSIVE|EMISSION|OPACITY|ALPHA|DISPLACEMENT|HEIGHT|DISP|D|N|M|R|E|A)$/i, '')
    const core = base.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]+/g, '')
    if (!groups.has(core)) groups.set(core, [])
    groups.get(core)!.push(file)
  })
  return [...groups.entries()].map(([core, group]) => {
    const channels: TextureChannels = {}
    group.forEach(file => {
      const ch = getTextureChannel(file.fileName)
      if (ch && file.url) (channels as any)[ch] = file.url
    })
    return { core, channels }
  }).sort((a, b) => {
    const aNum = a.core.match(/\d+$/)?.[0] || '9999'
    const bNum = b.core.match(/\d+$/)?.[0] || '9999'
    return parseInt(aNum) - parseInt(bNum)
  })
}

const applyTextures = async (mesh: THREE.Mesh, files: TextureFile[], force = false) => {
  if (!files || files.length === 0) return
  const meshName = mesh.name || '(unnamed-mesh)'
  const descriptiveFiles = extractDescriptiveFiles(files)
  const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
  await Promise.all(materials.map(async (material: any) => {
    if (!material) return

    // 1. 优先按材质名匹配 T_xxx_D / _N / _M 等通道贴图
    let channels: TextureChannels = material.name
      ? matchTextureChannels(material.name, files)
      : {}

    // 2. 单材质使用完整度最高的同核心贴图组，避免依赖某一种固定前缀。
    if (materialCount === 1 && Object.keys(channels).length === 0) {
      channels = bestSingleMaterialChannels(files)
    }

    // 2b. 多材质模型名称匹配失败时，按材质出现顺序依次分配纹理组（避免所有材质用同一组或无纹理）
    if (materialCount > 1 && Object.keys(channels).length === 0) {
      const orderedGroups = getOrderedTextureGroups(files)
      if (orderedGroups.length > 0) {
        const assigned = orderedGroups[textureAssignIndex % orderedGroups.length]
        channels = assigned.channels
        console.log(`[ModelViewer] order-fallback material#${textureAssignIndex} core=${assigned.core}`)
      }
      textureAssignIndex++
    }

    // 3. 仍没有规范材质贴图时，才使用描述性主贴图兜底。
    const fellBack = materialCount === 1 && Object.keys(channels).length === 0 && descriptiveFiles.length > 0
    if (fellBack) {
      channels = { diffuse: descriptiveFiles[0].url }
    }
    console.log(`[ModelViewer] material="${material.name || '(unnamed)'}" count=${materialCount} fellBack=${fellBack} channels=`, channels)
    ;(window as any).__textureDebug = (window as any).__textureDebug || []
    ;(window as any).__meshes = (window as any).__meshes || []
    // 调试：记录mesh几何信息和UV范围
    let vertCount = 0
    let bbox = null
    let uvMin = null
    let uvMax = null
    try {
      if (mesh.geometry) {
        vertCount = mesh.geometry.attributes.position?.count || 0
        mesh.geometry.computeBoundingBox()
        bbox = mesh.geometry.boundingBox ? {
          min: [mesh.geometry.boundingBox.min.x, mesh.geometry.boundingBox.min.y, mesh.geometry.boundingBox.min.z],
          max: [mesh.geometry.boundingBox.max.x, mesh.geometry.boundingBox.max.y, mesh.geometry.boundingBox.max.z]
        } : null
        const uvAttr = mesh.geometry.attributes.uv
        if (uvAttr) {
          uvMin = [uvAttr.array[0], uvAttr.array[1]]
          uvMax = [uvAttr.array[0], uvAttr.array[1]]
          for (let i = 0; i < uvAttr.count; i++) {
            const u = uvAttr.array[i * 2]
            const v = uvAttr.array[i * 2 + 1]
            if (u < uvMin[0]) uvMin[0] = u
            if (v < uvMin[1]) uvMin[1] = v
            if (u > uvMax[0]) uvMax[0] = u
            if (v > uvMax[1]) uvMax[1] = v
          }
        }
      }
    } catch(e) {}
    ;(window as any).__meshes.push({
      name: mesh.name, vertices: vertCount, bbox, uvMin, uvMax, material: material.name
    })
    // 调试：记录材质应用纹理前的所有纹理属性详情
    const beforeTex: any = {}
    for (const key of ['map', 'normalMap', 'roughnessMap', 'metalnessMap', 'bumpMap', 'aoMap', 'emissiveMap', 'alphaMap', 'displacementMap']) {
      const tex = material[key]
      if (tex && tex.isTexture) {
        beforeTex[key] = {
          wrapS: tex.wrapS, wrapT: tex.wrapT, flipY: tex.flipY,
          repeat: tex.repeat ? [tex.repeat.x, tex.repeat.y] : null,
          imageSize: tex.image ? [tex.image.width, tex.image.height] : null
        }
      }
    }
    ;(window as any).__textureDebug.push({
      mesh: mesh.name || '(unnamed-mesh)',
      material: material.name || '(unnamed)',
      materialType: material.type,
      count: materialCount,
      channels: Object.keys(channels),
      diffuse: channels.diffuse ? channels.diffuse.substring(0, 100) : null,
      beforeTextures: beforeTex
    })

    const isPbr = material.isMeshStandardMaterial || material.isMeshPhysicalMaterial
    const shouldApply = (prop: string, url?: string) => {
      if (!url) return false
      if (force) return true
      return !material[prop]
    }
    const pendingMaps: Promise<boolean>[] = []
    if (shouldApply('map', channels.diffuse)) pendingMaps.push(applyMap(material, 'map', channels.diffuse!))
    if (isPbr) {
      if (shouldApply('normalMap', channels.normal)) pendingMaps.push(applyMap(material, 'normalMap', channels.normal!))
      if (shouldApply('roughnessMap', channels.roughness)) pendingMaps.push(applyMap(material, 'roughnessMap', channels.roughness!))
      if (shouldApply('metalnessMap', channels.metalness)) pendingMaps.push(applyMap(material, 'metalnessMap', channels.metalness!))
      if (shouldApply('aoMap', channels.ao)) pendingMaps.push(applyMap(material, 'aoMap', channels.ao!))
      if (shouldApply('emissiveMap', channels.emissive)) pendingMaps.push(applyMap(material, 'emissiveMap', channels.emissive!))
      if (shouldApply('alphaMap', channels.alpha)) pendingMaps.push(applyMap(material, 'alphaMap', channels.alpha!))
      if (shouldApply('displacementMap', channels.displacement)) pendingMaps.push(applyMap(material, 'displacementMap', channels.displacement!))
    }
    await Promise.all(pendingMaps)

    // FBX 源法线贴图多为 DirectX 风格（绿轴向下），需翻转 Y 轴以匹配 Three.js/OpenGL
    if (channels.normal && props.textureFlipY && material.normalScale) {
      material.normalScale.set(1, -1)
      material.needsUpdate = true
    }

    // 调试：记录材质纹理详情
    ;(window as any).__materialDebug = (window as any).__materialDebug || []
    const texDebug: any = {}
    for (const key of Object.keys(material)) {
      const tex = material[key]
      if (tex && tex.isTexture) {
        texDebug[key] = {
          wrapS: tex.wrapS, wrapT: tex.wrapT, flipY: tex.flipY,
          repeat: tex.repeat ? [tex.repeat.x, tex.repeat.y] : null,
          imageSize: tex.image ? [tex.image.width, tex.image.height] : null,
          imageSrc: tex.image ? (tex.image.src || tex.image.currentSrc || 'canvas') : null
        }
      }
    }
    ;(window as any).__materialDebug.push({
      mesh: mesh.name, material: material.name, type: material.type,
      color: material.color ? '#' + material.color.getHexString() : null,
      metalness: material.metalness, roughness: material.roughness,
      textures: texDebug
    })

    // PBR 参数以实际通道为准，不再用“单/多材质”猜测材质类型。
    // 明确上传了 M 通道时必须启用金属工作流，否则青铜器会发黑、发哑。
    if (channels.metalness) {
      material.metalness = 1.0
      material.envMapIntensity = materialCount > 1 ? 1.4 : 1.0
      if (!channels.roughness) material.roughness = 0.42
    } else {
      material.metalness = 0.0
      material.envMapIntensity = materialCount === 1 ? 0.35 : 0.45
      if (!channels.roughness) material.roughness = materialCount === 1 ? 0.62 : 0.72
    }

    if (material.color) material.color.set(0xffffff)
    material.needsUpdate = true
  }))
}

// ===================== Camera =====================
const defaultViewDirection = new THREE.Vector3(1, 0.42, 1.35).normalize()

const getModelFit = (viewDirection = defaultViewDirection) => {
  if (!currentModel || !camera) return null
  const box = new THREE.Box3().setFromObject(currentModel)
  if (box.isEmpty()) return null

  const sphere = box.getBoundingSphere(new THREE.Sphere())
  const verticalFov = THREE.MathUtils.degToRad(camera.fov)
  const horizontalFov = 2 * Math.atan(Math.tan(verticalFov / 2) * camera.aspect)
  const direction = viewDirection.clone().normalize()
  let right = new THREE.Vector3().crossVectors(camera.up, direction)
  if (right.lengthSq() < 0.0001) right = new THREE.Vector3(1, 0, 0)
  else right.normalize()
  const up = new THREE.Vector3().crossVectors(direction, right).normalize()
  const tanVertical = Math.tan(verticalFov / 2)
  const tanHorizontal = Math.tan(horizontalFov / 2)
  let distance = 0
  let geometryPointCount = 0
  const worldPoint = new THREE.Vector3()
  const relative = new THREE.Vector3()
  currentModel.updateMatrixWorld(true)

  // 使用真实几何点而不是整个场景 AABB 的空角。长车、L 形或斜置模型因此不会被过度缩小。
  currentModel.traverse((node) => {
    if (!(node instanceof THREE.Mesh)) return
    const positions = node.geometry?.getAttribute('position')
    if (!positions?.count) return
    const step = Math.max(1, Math.ceil(positions.count / 750000))
    for (let index = 0; index < positions.count; index += step) {
      worldPoint.fromBufferAttribute(positions as THREE.BufferAttribute, index).applyMatrix4(node.matrixWorld)
      relative.copy(worldPoint).sub(sphere.center)
      const depth = relative.dot(direction)
      distance = Math.max(
        distance,
        depth + Math.abs(relative.dot(right)) / tanHorizontal,
        depth + Math.abs(relative.dot(up)) / tanVertical
      )
      geometryPointCount += 1
    }
  })

  // 无可读几何时保留包围盒兜底。
  if (geometryPointCount === 0) {
    for (const x of [box.min.x, box.max.x]) {
      for (const y of [box.min.y, box.max.y]) {
        for (const z of [box.min.z, box.max.z]) {
          relative.set(x, y, z).sub(sphere.center)
          const depth = relative.dot(direction)
          distance = Math.max(
            distance,
            depth + Math.abs(relative.dot(right)) / tanHorizontal,
            depth + Math.abs(relative.dot(up)) / tanVertical
          )
        }
      }
    }
  }
  distance *= 1.12
  return { center: sphere.center, radius: sphere.radius, distance }
}

const resetCamera = () => {
  if (!camera || !controls) return
  autoFitCamera = true
  const fit = getModelFit()
  if (!fit) {
    camera.position.set(5, 5, 8)
    controls.target.set(0, 3, 0)
    controls.update()
    return
  }

  // 稍微偏向右上方的三分之四视角，同时按完整包围球自适应取景。
  camera.position.copy(fit.center).addScaledVector(defaultViewDirection, fit.distance)
  controls.target.copy(fit.center)
  controls.minDistance = Math.max(0.5, fit.radius * 0.18)
  controls.maxDistance = Math.max(40, fit.radius * 12)
  controls.update()
}

const applyCameraState = (state: { position: number[]; target: number[] }) => {
  if (!camera || !controls) return
  camera.position.set(state.position[0], state.position[1], state.position[2])
  controls.target.set(state.target[0], state.target[1], state.target[2])
  controls.update()
}

const isCameraStateUsable = (state: { position: number[]; target: number[] }) => {
  if (!state?.position?.length || !state?.target?.length) return false
  const position = new THREE.Vector3(...state.position as [number, number, number])
  const target = new THREE.Vector3(...state.target as [number, number, number])
  const distance = position.distanceTo(target)
  if (!Number.isFinite(distance) || distance <= 0) return false

  const direction = position.clone().sub(target).normalize()
  const fit = getModelFit(direction)
  if (!fit) return distance >= 7 && distance <= 40
  const targetIsNearModel = target.distanceTo(fit.center) <= fit.radius * 0.7
  return targetIsNearModel && distance >= fit.distance * 0.9 && distance <= fit.radius * 12
}

const getCameraState = () => ({
  position: [camera.position.x, camera.position.y, camera.position.z],
  target: [controls.target.x, controls.target.y, controls.target.z]
})

// ===================== Lighting =====================
const applyLighting = () => {
  if (!scene) return
  if (scene.background instanceof THREE.Color) {
    scene.background.set(lighting.background)
  } else {
    scene.background = new THREE.Color(lighting.background)
  }
  if (ambientLight) ambientLight.intensity = lighting.ambient
  if (dirLight) dirLight.intensity = lighting.directional
  if (fillLight) fillLight.intensity = lighting.fill
  if (rimLight) rimLight.intensity = lighting.rim
  if (hemiLight) hemiLight.intensity = lighting.hemisphere
  if (renderer) renderer.toneMappingExposure = lighting.exposure
}

const applyLightingPreset = (values: Partial<typeof defaultLighting>) => {
  Object.assign(lighting, values)
  applyLighting()
}

const loadLightingConfig = (config: Record<string, any>) => {
  Object.assign(lighting, defaultLighting)
  const hasExtendedLights = ['fill', 'rim', 'hemisphere', 'exposure'].some((key) => key in config)
  if (hasExtendedLights) Object.assign(lighting, config)
  else if (typeof config.background === 'string') lighting.background = config.background
  applyLighting()
}

const resetLighting = () => {
  Object.assign(lighting, defaultLighting)
  applyLighting()
}

const emitLighting = () => {
  applyLighting()
  emit('lighting-change', JSON.stringify({ ...lighting }))
}

const getLightingConfig = () => JSON.stringify({ ...lighting })

const refresh = () => {
  if (props.modelUrl) loadModel(props.modelUrl, props.format)
}

// ===================== Misc =====================
const toggleWireframe = () => {
  wireframe.value = !wireframe.value
  if (currentModel) {
    currentModel.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        const mesh = child as THREE.Mesh
        const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
        materials.forEach(m => {
          const mat = m as THREE.MeshStandardMaterial
          mat.wireframe = wireframe.value
        })
      }
    })
  }
}

const toggleAutoRotate = () => {
  autoRotate.value = !autoRotate.value
  if (controls) controls.autoRotate = autoRotate.value
}

const toggleGrid = () => {
  showGrid.value = !showGrid.value
  if (gridHelper) gridHelper.visible = showGrid.value
}

const screenshot = () => {
  if (!renderer) return
  const url = renderer.domElement.toDataURL('image/png')
  const link = document.createElement('a')
  link.href = url
  link.download = 'model-screenshot.png'
  link.click()
}

const handleFullscreenChange = () => {
  isFullscreen.value = document.fullscreenElement === containerRef.value
}

const toggleFullscreen = async () => {
  const container = containerRef.value
  if (!container) return
  try {
    if (document.fullscreenElement === container) await document.exitFullscreen()
    else await container.requestFullscreen()
  } catch (e) {
    console.warn('Unable to toggle fullscreen preview', e)
  }
}

const handleOutsideLightingPointer = (event: PointerEvent) => {
  if (!showLighting.value) return
  const target = event.target
  if (!(target instanceof Element)) return
  if (target.closest('.lighting-panel')) return
  if (target.closest('button[title="灯光设置"]')) return
  // 颜色选择器会挂载到 body，仍视为灯光面板内部交互。
  if (target.closest('.el-color-dropdown, .el-color-picker__panel')) return
  showLighting.value = false
}

const captureScreenshot = (): string => {
  if (!renderer) return ''
  return renderer.domElement.toDataURL('image/png')
}

const disposeModel = (model: THREE.Object3D) => {
  model.traverse((child) => {
    if (child instanceof THREE.Mesh) {
      child.geometry.dispose()
      const materials = Array.isArray(child.material) ? child.material : [child.material]
      materials.forEach(m => m.dispose())
    }
  })
}

// ===================== Lifecycle =====================
watch(() => props.modelUrl, (newUrl) => {
  if (newUrl) loadModel(newUrl, props.format)
})

watch(() => props.initialCamera, (val) => {
  if (val) {
    try {
      const parsedCamera = JSON.parse(val) as { position: number[]; target: number[] }
      savedInitialCamera.value = parsedCamera
      if (currentModel) {
        if (isCameraStateUsable(parsedCamera)) {
          applyCameraState(parsedCamera)
          autoFitCamera = false
        } else resetCamera()
      }
    } catch (e) { /* ignore */ }
  }
})

watch(() => props.initialLighting, (val) => {
  if (val) {
    try {
      const cfg = JSON.parse(val)
      loadLightingConfig(cfg)
    } catch (e) { /* ignore */ }
  }
})

watch(() => props.textureFiles, (files) => {
  // 贴图文件变更后强制重新应用，覆盖模型自带贴图以使用最新上传文件
  if (files && files.length > 0 && currentModel && !loading.value) {
    currentModel.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        applyTextures(child, files, true)
      }
    })
  }
}, { deep: true })

onMounted(() => {
  document.addEventListener('pointerdown', handleOutsideLightingPointer)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  init()
  if (props.initialLighting) {
    try {
      loadLightingConfig(JSON.parse(props.initialLighting))
    } catch (e) { /* ignore */ }
  }
  if (props.initialCamera) {
    try { savedInitialCamera.value = JSON.parse(props.initialCamera) } catch (e) { /* ignore */ }
  }
  if (props.modelUrl) {
    loadModel(props.modelUrl, props.format)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutsideLightingPointer)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  if (animationId) cancelAnimationFrame(animationId)
  if (currentModel) disposeModel(currentModel)
  if (renderer) {
    renderer.dispose()
    renderer.domElement.remove()
  }
  if (controls) controls.dispose()
})

defineExpose({ getCameraState, applyCameraState, resetCamera, getLightingConfig, applyLighting, captureScreenshot, refresh })
</script>

<style scoped>
.viewer-container { width: 100%; height: 100%; position: relative; overflow: hidden; background: #071326; container-type: inline-size; }
.viewer-container:fullscreen { width: 100vw; height: 100vh; border-radius: 0; }
.viewer-container:fullscreen .viewer-toolbar { top: 20px; right: 20px; left: 20px; }
.viewer-container:fullscreen .viewer-toolbar-left,
.viewer-container:fullscreen .viewer-toolbar-center,
.viewer-container:fullscreen .viewer-toolbar-right { flex-direction: row; align-items: center; gap: 10px; }
.viewer-container:fullscreen .viewer-toolbar-left :deep(.toolbar-label) { display: none; }
.viewer-container:fullscreen .viewer-toolbar-left :deep(.el-button),
.viewer-container:fullscreen .viewer-toolbar-center :deep(.el-button),
.viewer-container:fullscreen .viewer-toolbar-right :deep(.el-button) {
  width: 36px; min-width: 36px; height: 36px; min-height: 36px; padding: 0;
  border-radius: 50% !important;
}
.viewer-toolbar {
  position: absolute; top: 12px; right: 12px; left: 12px; z-index: 10;
  min-height: 32px; pointer-events: none;
}
.viewer-toolbar-left, .viewer-toolbar-center, .viewer-toolbar-right {
  position: absolute; top: 0; display: flex; align-items: center; gap: 8px; pointer-events: auto;
}
.viewer-toolbar-controls { display: contents; }
.viewer-toolbar-left { left: 0; gap: 6px; }
.viewer-toolbar-center { left: 50%; transform: translateX(-50%); }
.viewer-toolbar-right { right: 0; }
.viewer-toolbar :deep(.el-button + .el-button) { margin-left: 0; }
.viewer-toolbar-left :deep(.toolbar-label) { display: none; }
.viewer-toolbar-left :deep(.el-button),
.viewer-toolbar-center :deep(.el-button),
.viewer-toolbar-right :deep(.el-button) {
  width: 34px; min-width: 34px; height: 34px; min-height: 34px; padding: 0;
  border-radius: 50% !important;
  color: #c8f8e7; background: rgba(13, 39, 50, .92); border-color: rgba(110, 231, 190, .58);
  box-shadow: 0 5px 16px rgba(2, 8, 23, .32), inset 0 0 0 1px rgba(255, 255, 255, .035);
  backdrop-filter: blur(12px);
  transition: color .16s ease, border-color .16s ease, background-color .16s ease, box-shadow .16s ease, transform .16s ease;
}
.viewer-toolbar :deep(.el-button .el-icon) {
  color: inherit; font-size: 16px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, .5));
}
.viewer-toolbar :deep(.el-button .el-icon svg) {
  width: 1em; height: 1em; color: inherit; fill: currentColor; opacity: 1;
}
.viewer-toolbar-left :deep(.el-button:hover),
.viewer-toolbar-center :deep(.el-button:hover),
.viewer-toolbar-right :deep(.el-button:hover) {
  color: #fff; border-color: #8affd5; background: rgba(25, 132, 103, .96);
  box-shadow: 0 0 0 2px rgba(91, 238, 187, .14), 0 8px 24px rgba(22, 196, 139, .38);
  transform: translateY(-1px) scale(1.04);
}
.viewer-toolbar-left :deep(.el-button.is-active),
.viewer-toolbar-center :deep(.el-button.is-active),
.viewer-toolbar-right :deep(.el-button.is-active),
.viewer-toolbar-left :deep(.el-button.el-button--primary),
.viewer-toolbar-center :deep(.el-button.el-button--primary),
.viewer-toolbar-right :deep(.el-button.el-button--primary) {
  color: #fff; border-color: #9affdc; background: linear-gradient(145deg, #28ad84, #13765e);
  box-shadow: 0 0 0 2px rgba(91, 238, 187, .25), 0 0 19px rgba(24, 211, 149, .62), inset 0 1px 0 rgba(255, 255, 255, .2);
  transform: scale(1.08);
}
.viewer-toolbar-left :deep(.el-button.is-active .el-icon),
.viewer-toolbar-center :deep(.el-button.is-active .el-icon),
.viewer-toolbar-right :deep(.el-button.is-active .el-icon),
.viewer-toolbar :deep(.el-button.el-button--primary .el-icon) {
  filter: drop-shadow(0 0 5px rgba(255, 255, 255, .72));
}
.viewer-toolbar-left :deep(.el-button:active),
.viewer-toolbar-center :deep(.el-button:active),
.viewer-toolbar-right :deep(.el-button:active) {
  color: #fff; border-color: #b7ffe6; background: #0f6f58;
  box-shadow: 0 0 0 3px rgba(91, 238, 187, .2), 0 3px 10px rgba(2, 8, 23, .4);
  transform: translateY(0) scale(.94);
}
.viewer-toolbar-left :deep(.el-button:focus-visible),
.viewer-toolbar-center :deep(.el-button:focus-visible),
.viewer-toolbar-right :deep(.el-button:focus-visible) {
  outline: 2px solid #9bcfbe; outline-offset: 2px;
}
.viewer-toolbar-left :deep(.el-button.is-disabled),
.viewer-toolbar-center :deep(.el-button.is-disabled),
.viewer-toolbar-right :deep(.el-button.is-disabled) {
  color: #72877f; background: rgba(17, 34, 42, .68); border-color: rgba(125, 151, 142, .22);
  box-shadow: none; opacity: .58; transform: none;
}
@container (max-width: 720px) {
  .viewer-toolbar { top: 12px; right: 12px; bottom: 12px; left: 12px; }
  .viewer-toolbar-left {
    flex-direction: column;
    align-items: flex-start;
  }
  .viewer-toolbar-left :deep(.el-button) { width: 34px; min-width: 34px; height: 34px; min-height: 34px; padding: 0; border-radius: 50% !important; }
  .viewer-toolbar-controls { display: contents; }
  .viewer-toolbar-center { top: 0; left: 50%; flex-direction: row; gap: 6px; transform: translateX(-50%); }
  .viewer-toolbar-right { top: 0; right: 0; flex-direction: column; gap: 6px; }
  .viewer-toolbar-left { gap: 6px; }
  .lighting-panel { top: 50px; right: 48px; width: min(320px, calc(100% - 72px)); }
}
.lighting-panel {
  position: absolute; top: 58px; right: 12px; z-index: 20;
  width: 320px; padding: 16px;
  color: #e5edf9; background: rgba(8,15,30,.94);
  border: 1px solid rgba(148,163,184,.2); border-radius: 14px;
  box-shadow: 0 20px 54px rgba(0,0,0,.4); backdrop-filter: blur(16px);
}
.lp-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
.lp-header strong { display: block; font-size: 14px; }
.lp-header small { display: block; margin-top: 3px; color: #7f8ca3; font-size: 10px; font-weight: 400; }
.lp-header button { width: 26px; height: 26px; color: #94a3b8; background: transparent; border: 0; border-radius: 7px; cursor: pointer; font-size: 20px; line-height: 1; }
.lp-header button:hover { color: #fff; background: rgba(255,255,255,.08); }
.lp-presets { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin-bottom: 14px; }
.lp-presets button { padding: 6px 2px; color: #b9c7da; background: rgba(255,255,255,.05); border: 1px solid rgba(148,163,184,.16); border-radius: 7px; cursor: pointer; font-size: 11px; }
.lp-presets button:hover { color: #fff; background: rgba(35,139,112,.35); border-color: rgba(96,165,250,.55); }
.lp-row { display: grid; grid-template-columns: 72px 1fr 34px; align-items: center; gap: 8px; min-height: 34px; font-size: 11px; }
.lp-row > span { display: flex; align-items: center; gap: 6px; color: #c6d2e2; }
.lp-row :deep(.el-slider) { width: 100%; }
.lp-row b { color: #8fa1b8; font-size: 10px; font-weight: 500; text-align: right; }
.light-dot { width: 7px; height: 7px; border-radius: 50%; box-shadow: 0 0 8px currentColor; }
.light-dot.ambient { color: #c4b5fd; background: currentColor; }
.light-dot.key { color: #fde68a; background: currentColor; }
.light-dot.fill { color: #9bcfbe; background: currentColor; }
.light-dot.rim { color: #62b599; background: currentColor; }
.light-dot.hemi { color: #a7f3d0; background: currentColor; }
.lp-color-row { display: flex; align-items: center; justify-content: space-between; margin: 6px 0 12px; padding-top: 10px; color: #c6d2e2; border-top: 1px solid rgba(148,163,184,.13); font-size: 11px; }
.lp-actions { display: flex; justify-content: flex-end; gap: 8px; }
.viewer-loading, .viewer-error {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  color: #fff; z-index: 5; background: rgba(0,0,0,0.5); padding: 24px; border-radius: 8px;
}
.viewer-error { color: #f56c6c; }
.viewer-info {
  position: absolute; top: 58px; right: 12px; z-index: 15;
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px 18px;
  min-width: 210px; padding: 14px;
  color: #e2e8f0; background: rgba(8,15,30,.92);
  border: 1px solid rgba(148,163,184,.2); border-radius: 12px;
  box-shadow: 0 16px 40px rgba(0,0,0,.35); backdrop-filter: blur(14px);
}
.viewer-info div { display: flex; flex-direction: column; gap: 2px; }
.viewer-info span { color: #7f8ca3; font-size: 10px; }
.viewer-info strong { color: #f8fafc; font-size: 13px; font-weight: 600; }
</style>
