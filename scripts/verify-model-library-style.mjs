import { writeFile } from 'node:fs/promises'

const port = process.env.CDP_PORT || '9340'
const baseUrl = process.env.PLATFORM_URL || 'http://127.0.0.1:8088'
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
let targets
for (let attempt = 0; attempt < 30; attempt += 1) {
  try {
    targets = await fetch(`http://127.0.0.1:${port}/json/list`).then((response) => response.json())
    break
  } catch { await sleep(500) }
}
const target = targets?.find((item) => item.type === 'page' && !item.url.startsWith('edge://'))
if (!target) throw new Error('Browser target was not found')
const socket = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})
let id = 1
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
  const requestId = id++
  pending.set(requestId, { resolve, reject })
  socket.send(JSON.stringify({ id: requestId, method, params }))
})
const evaluate = async (expression) => {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true })
  return result.result.value
}
await send('Page.enable')
await send('Network.enable')
await send('Network.setCacheDisabled', { cacheDisabled: true })
await send('Emulation.setDeviceMetricsOverride', { width: 1646, height: 910, deviceScaleFactor: 1, mobile: false })
await send('Page.navigate', { url: `${baseUrl}/models?style_check=${Date.now()}` })
for (let attempt = 0; attempt < 30; attempt += 1) {
  await sleep(400)
  if (await evaluate(`document.querySelectorAll('.model-card').length > 0`)) break
}
const style = await evaluate(`(() => {
  const card = document.querySelector('.model-card')
  const info = document.querySelector('.model-info')
  const name = document.querySelector('.model-name')
  if (!card || !info || !name) return null
  return {
    count: document.querySelectorAll('.model-card').length,
    cardBackground: getComputedStyle(card).backgroundColor,
    infoBackground: getComputedStyle(info).backgroundColor,
    nameColor: getComputedStyle(name).color,
    viewport: [innerWidth, innerHeight]
  }
})()`)
const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
await writeFile(new URL('../.ui-review/model-library-light.png', import.meta.url), Buffer.from(screenshot.data, 'base64'))
socket.close()
if (!style || style.count < 1) throw new Error('Model cards were not rendered')
if (!style.cardBackground.startsWith('rgba(255, 255, 255') && style.cardBackground !== 'rgb(255, 255, 255)') {
  throw new Error(`Model card is not using the platform light surface: ${JSON.stringify(style)}`)
}
if (style.infoBackground !== 'rgb(255, 255, 255)') {
  throw new Error(`Model cards are not using the platform light surface: ${JSON.stringify(style)}`)
}
console.log(JSON.stringify(style, null, 2))
