import { writeFile } from 'node:fs/promises'

const cdpPort = process.env.CDP_PORT || '9340'
const baseUrl = process.env.PLATFORM_URL || 'http://127.0.0.1:8088'
const viewportWidth = Number(process.env.QA_WIDTH || 1292)
const viewportHeight = Number(process.env.QA_HEIGHT || 910)
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

let targets
for (let attempt = 0; attempt < 30; attempt += 1) {
  try {
    targets = await fetch(`http://127.0.0.1:${cdpPort}/json/list`).then((response) => response.json())
    break
  } catch {
    await sleep(500)
  }
}
const target = targets?.find((item) => item.type === 'page' && !item.url.startsWith('edge://'))
if (!target) throw new Error('Browser page target was not found')

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
const evaluate = async (expression) => {
  const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text)
  return result.result.value
}
const capture = async (path) => {
  const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  await writeFile(path, Buffer.from(result.data, 'base64'))
}
const readLayout = () => evaluate(`(() => {
  const rect = (selector) => {
    const node = document.querySelector(selector)
    if (!node) return null
    const box = node.getBoundingClientRect()
    return { left: Math.round(box.left), right: Math.round(box.right), top: Math.round(box.top), width: Math.round(box.width) }
  }
  const left = rect('.viewer-toolbar-left')
  const center = rect('.viewer-toolbar-center')
  const right = rect('.viewer-toolbar-right')
  return {
    url: location.href,
    state: document.querySelector('.viewer-container')?.dataset.modelState,
    fullscreen: Boolean(document.fullscreenElement),
    left,
    center,
    right,
    noOverlap: Boolean(left && center && right && left.right + 8 <= center.left && center.right + 8 <= right.left),
    labelsVisible: [...document.querySelectorAll('.viewer-toolbar-left .toolbar-label')].every((node) => getComputedStyle(node).display !== 'none'),
    titles: [...document.querySelectorAll('.viewer-toolbar button')].map((node) => node.title).filter(Boolean)
  }
})()`)

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Network.setCacheDisabled', { cacheDisabled: true })
await send('Emulation.setDeviceMetricsOverride', { width: viewportWidth, height: viewportHeight, deviceScaleFactor: 1, mobile: false })
await send('Page.navigate', { url: `${baseUrl}/models/11?version=1&toolbar_check=${Date.now()}` })
for (let attempt = 0; attempt < 30; attempt += 1) {
  await sleep(500)
  if (await evaluate(`document.querySelector('.viewer-container')?.dataset.modelState === 'ready'`)) break
}
const normal = await readLayout()
await capture(new URL('../.ui-review/toolbar-layout-normal.png', import.meta.url))

const fullscreenButton = await evaluate(`(() => {
  const button = document.querySelector('.viewer-toolbar-right button[title="全屏预览"]')
  if (!button) return null
  const box = button.getBoundingClientRect()
  return { x: box.left + box.width / 2, y: box.top + box.height / 2 }
})()`)
if (!fullscreenButton) throw new Error('Fullscreen button was not found')
await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: fullscreenButton.x, y: fullscreenButton.y })
await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: fullscreenButton.x, y: fullscreenButton.y, button: 'left', clickCount: 1 })
await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: fullscreenButton.x, y: fullscreenButton.y, button: 'left', clickCount: 1 })
await sleep(800)
const fullscreen = await readLayout()
await capture(new URL('../.ui-review/toolbar-layout-fullscreen.png', import.meta.url))
await evaluate(`document.fullscreenElement ? document.exitFullscreen() : Promise.resolve()`)
socket.close()

if (normal.state !== 'ready' || fullscreen.state !== 'ready') throw new Error('Model viewer was not ready')
if (!normal.noOverlap || !fullscreen.noOverlap) throw new Error('Toolbar groups overlap')
if (viewportWidth >= 1200 && !normal.labelsVisible) throw new Error('Normal viewer labels are unexpectedly hidden')
if (!fullscreen.fullscreen) throw new Error('Fullscreen mode did not open')
console.log(JSON.stringify({ normal, fullscreen }, null, 2))
