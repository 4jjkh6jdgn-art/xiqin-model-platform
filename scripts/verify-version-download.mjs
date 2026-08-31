const cdpPort = process.env.CDP_PORT || '9340'
const modelId = process.env.XIQIN_TEST_MODEL_ID || '11'
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const targets = await fetch(`http://127.0.0.1:${cdpPort}/json/list`).then((response) => response.json())
const target = targets.find((item) => item.type === 'page' && item.url.startsWith('http://127.0.0.1:8088'))
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
const evaluate = async (expression, awaitPromise = false) => {
  const response = await send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true })
  if (response.exceptionDetails) throw new Error(response.exceptionDetails.text)
  return response.result.value
}

await send('Runtime.enable')
await send('Page.enable')
await evaluate(`location.href = 'http://127.0.0.1:8088/models/${modelId}?version=1'`)
await sleep(4500)

const result = await evaluate(`(async () => {
  const token = localStorage.getItem('xiqin_token')
  if (!token) throw new Error('Browser session is not logged in')
  const headers = { Authorization: 'Bearer ' + token }
  const versionsResponse = await fetch('/api/models/${modelId}/versions', { headers })
  const detailResponse = await fetch('/api/models/${modelId}?version=1', { headers })
  const libraryResponse = await fetch('/api/models?page=0&size=20', { headers })
  const versions = await versionsResponse.json()
  const detail = await detailResponse.json()
  const library = await libraryResponse.json()
  const archiveResponse = await fetch('/api/models/${modelId}/versions/1/download.zip', { headers })
  const archive = new Uint8Array(await archiveResponse.arrayBuffer())
  const archiveView = new DataView(archive.buffer, archive.byteOffset, archive.byteLength)
  let eocdOffset = -1
  for (let index = archive.byteLength - 22; index >= Math.max(0, archive.byteLength - 65557); index -= 1) {
    if (archiveView.getUint32(index, true) === 0x06054b50) { eocdOffset = index; break }
  }
  const archiveEntryCount = eocdOffset >= 0 ? archiveView.getUint16(eocdOffset + 10, true) : 0
  let directoryOffset = eocdOffset >= 0 ? archiveView.getUint32(eocdOffset + 16, true) : 0
  const archiveEntries = []
  for (let index = 0; index < archiveEntryCount; index += 1) {
    if (archiveView.getUint32(directoryOffset, true) !== 0x02014b50) break
    const nameLength = archiveView.getUint16(directoryOffset + 28, true)
    const extraLength = archiveView.getUint16(directoryOffset + 30, true)
    const commentLength = archiveView.getUint16(directoryOffset + 32, true)
    archiveEntries.push(new TextDecoder().decode(archive.slice(directoryOffset + 46, directoryOffset + 46 + nameLength)))
    directoryOffset += 46 + nameLength + extraLength + commentLength
  }
  const deleteButtons = Array.from(document.querySelectorAll('.thumbnail-delete'))
  return {
    versionsStatus: versionsResponse.status,
    versions: (versions.data || []).map((item) => item.versionNum),
    detailStatus: detailResponse.status,
    selectedVersion: detail.data?.version,
    latestVersion: detail.data?.latestVersion,
    detailFileCount: detail.data?.files?.length || 0,
    libraryStatus: libraryResponse.status,
    libraryCount: library.data?.list?.length || 0,
    archiveStatus: archiveResponse.status,
    archiveBytes: archive.byteLength,
    archiveMagic: Array.from(archive.slice(0, 4)),
    archiveEntryCount,
    archiveEntries,
    archiveDisposition: archiveResponse.headers.get('content-disposition'),
    hasUpdateButton: Array.from(document.querySelectorAll('button')).some((item) => item.textContent?.trim() === '更新'),
    hasVersionZipButton: document.body.innerText.includes('下载 v1 ZIP'),
    thumbnailDeleteButtons: deleteButtons.length,
    protectedThumbnailButtons: deleteButtons.filter((item) => item.disabled).length,
    pageTextHasError: document.body.innerText.includes('模型加载失败')
  }
})()`, true)

console.log(JSON.stringify(result, null, 2))
const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
await writeFile(new URL('../.ui-review/version-feature.png', import.meta.url), Buffer.from(screenshot.data, 'base64'))
socket.close()
import { writeFile } from 'node:fs/promises'
