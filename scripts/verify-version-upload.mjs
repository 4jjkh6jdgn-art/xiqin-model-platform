import { readFile } from 'node:fs/promises'

const cdpPort = process.env.CDP_PORT || '9340'
const baseUrl = process.env.PLATFORM_URL || 'http://127.0.0.1:8088'
const samplePath = new URL('../.ui-review/model5.glb', import.meta.url)

const targets = await fetch(`http://127.0.0.1:${cdpPort}/json/list`).then((response) => response.json())
const target = targets.find((item) => item.type === 'page' && item.url.startsWith(baseUrl))
if (!target) throw new Error('Platform browser target was not found')
const socket = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})
let nextId = 1
const pending = new Map()
socket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data)
  if (!message.id || !pending.has(message.id)) return
  const handlers = pending.get(message.id)
  pending.delete(message.id)
  if (message.error) handlers.reject(new Error(message.error.message))
  else handlers.resolve(message.result)
})
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = nextId++
  pending.set(id, { resolve, reject })
  socket.send(JSON.stringify({ id, method, params }))
})
const evaluated = await send('Runtime.evaluate', {
  expression: `localStorage.getItem('xiqin_token')`,
  returnByValue: true,
})
const token = evaluated.result.value
socket.close()
if (!token) throw new Error('Browser session is not logged in')

const headers = { Authorization: `Bearer ${token}` }
const requestJson = async (path, options = {}) => {
  const response = await fetch(`${baseUrl}/api${path}`, { ...options, headers: { ...headers, ...options.headers } })
  const result = await response.json()
  if (!response.ok || result.code !== 200) throw new Error(result.message || `HTTP ${response.status}`)
  return result.data
}
const archiveEntries = async (modelId, version) => {
  const response = await fetch(`${baseUrl}/api/models/${modelId}/versions/${version}/download.zip`, { headers })
  if (!response.ok) throw new Error(`Archive HTTP ${response.status}`)
  const bytes = new Uint8Array(await response.arrayBuffer())
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  let eocd = -1
  for (let index = bytes.byteLength - 22; index >= Math.max(0, bytes.byteLength - 65557); index -= 1) {
    if (view.getUint32(index, true) === 0x06054b50) { eocd = index; break }
  }
  const count = eocd >= 0 ? view.getUint16(eocd + 10, true) : 0
  let offset = eocd >= 0 ? view.getUint32(eocd + 16, true) : 0
  const entries = []
  for (let index = 0; index < count; index += 1) {
    const nameLength = view.getUint16(offset + 28, true)
    const extraLength = view.getUint16(offset + 30, true)
    const commentLength = view.getUint16(offset + 32, true)
    entries.push(new TextDecoder().decode(bytes.slice(offset + 46, offset + 46 + nameLength)))
    offset += 46 + nameLength + extraLength + commentLength
  }
  return entries
}

const sample = await readFile(samplePath)
const testName = `__版本功能验收_${Date.now()}`
let modelId
try {
  const initial = new FormData()
  initial.append('files', new File([sample], 'artifact.glb', { type: 'model/gltf-binary' }))
  initial.append('files', new File(['version one'], 'readme.txt', { type: 'text/plain' }))
  initial.append('modelName', testName)
  initial.append('filePaths', JSON.stringify(['QA_FOLDER/v1/artifact.glb', 'QA_FOLDER/v1/readme.txt']))
  initial.append('fileTypes', JSON.stringify({ 'QA_FOLDER/v1/artifact.glb': 'display', 'QA_FOLDER/v1/readme.txt': 'other' }))
  const created = await requestJson('/models/upload-folder', { method: 'POST', body: initial })
  modelId = created.id

  const update = new FormData()
  update.append('files', new File([sample], 'artifact.glb', { type: 'model/gltf-binary' }))
  update.append('files', new File(['version two'], 'readme.txt', { type: 'text/plain' }))
  update.append('changeLog', '自动验收第二版')
  update.append('filePaths', JSON.stringify(['QA_FOLDER/v2/artifact.glb', 'QA_FOLDER/v2/readme.txt']))
  update.append('fileTypes', JSON.stringify({ 'QA_FOLDER/v2/artifact.glb': 'display', 'QA_FOLDER/v2/readme.txt': 'other' }))
  const version = await requestJson(`/models/${modelId}/versions/upload`, { method: 'POST', body: update })

  const versions = await requestJson(`/models/${modelId}/versions`)
  const v1 = await requestJson(`/models/${modelId}?version=1`)
  const v2 = await requestJson(`/models/${modelId}?version=2`)
  const v1Archive = await archiveEntries(modelId, 1)
  const v2Archive = await archiveEntries(modelId, 2)
  console.log(JSON.stringify({
    createdVersion: version.versionNum,
    versionOptions: versions.map((item) => item.versionNum),
    v1Files: v1.files.map((item) => item.fileName),
    v2Files: v2.files.map((item) => item.fileName),
    v1Archive,
    v2Archive,
    oldVersionPreserved: v1Archive.includes('QA_FOLDER/v1/readme.txt'),
    newVersionSeparated: v2Archive.includes('QA_FOLDER/v2/readme.txt') && !v2Archive.includes('QA_FOLDER/v1/readme.txt'),
  }, null, 2))
} finally {
  if (modelId) await requestJson(`/models/${modelId}`, { method: 'DELETE' })
}
