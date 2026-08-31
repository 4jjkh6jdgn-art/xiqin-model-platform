export type ModelFileKind = 'display' | 'texture' | 'reference' | 'other'
export type TextureChannel = 'diffuse' | 'normal' | 'roughness' | 'metalness' | 'ao' | 'emissive' | 'alpha' | 'displacement'

export interface NamedFile {
  fileName: string
  url: string
}

export interface FileClassification {
  kind: ModelFileKind
  reason: string
  channel?: TextureChannel
}

export const DISPLAY_EXTS = ['fbx', 'obj', 'gltf', 'glb', 'stl', 'ply', 'dae', '3mf', 'usdz']
export const IMAGE_EXTS = ['png', 'jpg', 'jpeg', 'tga', 'bmp', 'tiff', 'tif', 'exr', 'hdr', 'psd', 'webp']

const channelPattern = /(?:^|[_ .-])(BASE[ _-]?COLOR|BASECOLOR|DIFFUSE|ALBEDO|COLOR|NORMAL|NORMALGL|NORMALDX|BUMP|METALLIC|METALNESS|METAL|ROUGHNESS|ROUGH|AO|OCCLUSION|AMBIENT|EMISSIVE|EMISSION|OPACITY|ALPHA|DISPLACEMENT|HEIGHT|DISP|D|N|M|R|E|A)$/i
const referencePattern = /(截图|效果图|预览|缩略图|渲染图|参考图|screenshot|preview|thumbnail|render|reference)/i

export const getFileExtension = (name: string) => name.split('.').pop()?.toLowerCase() || ''

const stripExtension = (name: string) => (name || '').replace(/\.[^.]+$/, '')

export const getTextureChannel = (name: string): TextureChannel | null => {
  const base = stripExtension(name)
  const match = base.match(channelPattern)
  if (!match) return null
  const token = match[1].replace(/[ _-]/g, '').toUpperCase()
  if (['D', 'DIFFUSE', 'ALBEDO', 'BASECOLOR', 'COLOR'].includes(token)) return 'diffuse'
  if (['N', 'NORMAL', 'NORMALGL', 'NORMALDX', 'BUMP'].includes(token)) return 'normal'
  if (['M', 'METALLIC', 'METALNESS', 'METAL'].includes(token)) return 'metalness'
  if (['R', 'ROUGHNESS', 'ROUGH'].includes(token)) return 'roughness'
  if (['AO', 'OCCLUSION', 'AMBIENT'].includes(token)) return 'ao'
  if (['E', 'EMISSIVE', 'EMISSION'].includes(token)) return 'emissive'
  if (['A', 'ALPHA', 'OPACITY'].includes(token)) return 'alpha'
  if (['DISP', 'DISPLACEMENT', 'HEIGHT'].includes(token)) return 'displacement'
  return null
}

export const normalizeAssetCore = (name: string): string => {
  let base = stripExtension(name).trim()
  base = base.replace(/^(SM|SK|T|M|MI|MAT|MTL|TEX)[ _.-]+/i, '')
  base = base.replace(channelPattern, '')
  return base.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]+/g, '')
}

const numericTokens = (value: string) => value.match(/\d+/g) || []

const coreScore = (materialCore: string, textureCore: string) => {
  if (!materialCore || !textureCore) return -1
  if (materialCore === textureCore) return 1000
  const materialNumbers = numericTokens(materialCore)
  const textureNumbers = numericTokens(textureCore)
  if (materialNumbers.length && textureNumbers.length && materialNumbers.join(',') !== textureNumbers.join(',')) return -1
  if (materialCore.includes(textureCore) || textureCore.includes(materialCore)) {
    return 500 + Math.min(materialCore.length, textureCore.length)
  }
  const materialText = materialCore.replace(/\d+/g, '')
  const textureText = textureCore.replace(/\d+/g, '')
  if (materialText && materialText === textureText) return 400
  return -1
}

export const classifyModelFile = (fileName: string, displayFileNames: string[] = []): FileClassification => {
  const ext = getFileExtension(fileName)
  if (DISPLAY_EXTS.includes(ext)) return { kind: 'display', reason: '三维模型文件' }
  if (!IMAGE_EXTS.includes(ext)) return { kind: 'other', reason: '工程或附属文件' }
  if (referencePattern.test(stripExtension(fileName))) return { kind: 'reference', reason: '效果图或参考图' }

  const channel = getTextureChannel(fileName)
  if (channel) return { kind: 'texture', reason: `材质通道：${channel}`, channel }

  const imageCore = normalizeAssetCore(fileName)
  const belongsToModel = displayFileNames.some((displayName) => {
    const displayCore = normalizeAssetCore(displayName)
    return coreScore(displayCore, imageCore) >= 400
  })
  if (belongsToModel) return { kind: 'texture', reason: '名称与主模型关联' }
  return { kind: 'reference', reason: '未识别材质通道，按参考图处理' }
}

export const materialTextureFiles = <T extends NamedFile>(files: T[]): T[] => {
  const recognized = files.filter((file) => Boolean(getTextureChannel(file.fileName)))
  return recognized.length ? recognized : files.filter((file) => classifyModelFile(file.fileName).kind === 'texture')
}

export const matchTextureChannels = <T extends NamedFile>(materialName: string, files: T[]): Partial<Record<TextureChannel, string>> => {
  const materialCore = normalizeAssetCore(materialName)
  const groups = new Map<string, T[]>()
  files.forEach((file) => {
    const channel = getTextureChannel(file.fileName)
    if (!channel || !file.url) return
    const core = normalizeAssetCore(file.fileName)
    groups.set(core, [...(groups.get(core) || []), file])
  })

  const best = [...groups.entries()]
    .map(([core, group]) => ({ core, group, score: coreScore(materialCore, core) }))
    .filter((item) => item.score >= 0)
    .sort((a, b) => b.score - a.score || b.group.length - a.group.length)[0]
  if (!best) return {}

  const channels: Partial<Record<TextureChannel, string>> = {}
  best.group.forEach((file) => {
    const channel = getTextureChannel(file.fileName)
    if (channel && file.url) channels[channel] = file.url
  })
  return channels
}

export const bestSingleMaterialChannels = <T extends NamedFile>(files: T[]): Partial<Record<TextureChannel, string>> => {
  const groups = new Map<string, T[]>()
  files.forEach((file) => {
    if (!getTextureChannel(file.fileName) || !file.url) return
    const core = normalizeAssetCore(file.fileName)
    groups.set(core, [...(groups.get(core) || []), file])
  })
  const best = [...groups.values()].sort((a, b) => {
    const aDiffuse = a.some((file) => getTextureChannel(file.fileName) === 'diffuse') ? 10 : 0
    const bDiffuse = b.some((file) => getTextureChannel(file.fileName) === 'diffuse') ? 10 : 0
    return bDiffuse + b.length - (aDiffuse + a.length)
  })[0]
  if (!best) return {}
  const channels: Partial<Record<TextureChannel, string>> = {}
  best.forEach((file) => {
    const channel = getTextureChannel(file.fileName)
    if (channel && file.url) channels[channel] = file.url
  })
  return channels
}
