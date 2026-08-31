/**
 * 文件类型自动检测
 *
 * DISPLAY：可展示/可转换的 3D 模型格式
 * TEXTURE：纹理贴图格式
 * OTHER：其他类型
 */

const DISPLAY_FORMATS = new Set([
  'fbx',
  'obj',
  'gltf',
  'glb',
  'stl',
  'ply',
  'dae',
  '3mf',
]);

const TEXTURE_FORMATS = new Set([
  'png',
  'jpg',
  'jpeg',
  'tga',
  'bmp',
  'tiff',
  'exr',
  'hdr',
  'psd',
  'webp',
]);

/**
 * 根据文件名检测文件类型
 * @param {string} filename
 * @returns {'display' | 'texture' | 'other'}
 */
export function detectFileType(filename) {
  if (!filename || typeof filename !== 'string') return 'other';

  // 取最后一个点之后的部分作为扩展名（文件名可能含多个点，如 texture.diffuse.png）
  const dotIndex = filename.lastIndexOf('.');
  if (dotIndex === -1 || dotIndex === filename.length - 1) return 'other';

  const ext = filename.slice(dotIndex + 1).toLowerCase();

  if (DISPLAY_FORMATS.has(ext)) return 'display';
  if (TEXTURE_FORMATS.has(ext)) return 'texture';
  return 'other';
}
