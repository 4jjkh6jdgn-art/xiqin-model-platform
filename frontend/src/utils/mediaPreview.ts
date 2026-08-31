export type MediaPreviewType = 'image' | 'video'

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg', 'avif'])
const VIDEO_EXTENSIONS = new Set(['mp4', 'webm', 'ogg', 'ogv', 'mov', 'm4v'])

export const getFileExtension = (fileName: string | null | undefined = '') => {
  const cleanName = String(fileName || '').split(/[?#]/, 1)[0]
  const dotIndex = cleanName.lastIndexOf('.')
  return dotIndex >= 0 ? cleanName.slice(dotIndex + 1).toLowerCase() : ''
}

export const getMediaPreviewType = (fileName: string | null | undefined = '', mimeType: string | null | undefined = ''): MediaPreviewType | null => {
  const normalizedMime = String(mimeType || '').toLowerCase()
  if (normalizedMime.startsWith('image/')) return 'image'
  if (normalizedMime.startsWith('video/')) return 'video'

  const extension = getFileExtension(fileName)
  if (IMAGE_EXTENSIONS.has(extension)) return 'image'
  if (VIDEO_EXTENSIONS.has(extension)) return 'video'
  return null
}

export const canPreviewMedia = (fileName: string | null | undefined = '', mimeType: string | null | undefined = '') => Boolean(getMediaPreviewType(fileName, mimeType))

export const isPreviewableImage = (fileName: string | null | undefined = '', mimeType: string | null | undefined = '') => getMediaPreviewType(fileName, mimeType) === 'image'

export const isPreviewableVideo = (fileName: string | null | undefined = '', mimeType: string | null | undefined = '') => getMediaPreviewType(fileName, mimeType) === 'video'
