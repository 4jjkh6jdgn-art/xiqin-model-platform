const cdpPort = process.env.CDP_PORT || '9333'
const platformUrl = process.env.PLATFORM_URL || 'http://127.0.0.1:8088'
const username = process.env.XIQIN_TEST_USER
const password = process.env.XIQIN_TEST_PASSWORD

if (!username || !password) {
  throw new Error('Missing XIQIN_TEST_USER or XIQIN_TEST_PASSWORD')
}

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
const browserTarget = targets?.find((target) => target.type === 'page' && !target.url.startsWith('edge://'))
  || targets?.find((target) => target.type === 'page')
if (!browserTarget) throw new Error('Headless browser did not expose a page target')

const socket = new WebSocket(browserTarget.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})

let nextId = 1
const pending = new Map()
socket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data)
  if (!message.id || !pending.has(message.id)) return
  const { resolve, reject } = pending.get(message.id)
  pending.delete(message.id)
  if (message.error) reject(new Error(message.error.message))
  else resolve(message.result)
})

const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = nextId++
  pending.set(id, { resolve, reject })
  socket.send(JSON.stringify({ id, method, params }))
})

const evaluate = async (expression, awaitPromise = false) => {
  const response = await send('Runtime.evaluate', { expression, awaitPromise, returnByValue: true })
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text)
  }
  return response.result.value
}

await send('Page.enable')
await send('Runtime.enable')
await send('Page.navigate', { url: `${platformUrl}/login` })
let loginPageReady = false
let lastLoginPageState
for (let attempt = 0; attempt < 40; attempt += 1) {
  const state = await evaluate(`({ href: location.href, ready: document.readyState })`)
  lastLoginPageState = state
  if (state.href.startsWith(platformUrl) && state.ready === 'complete') {
    loginPageReady = true
    break
  }
  await sleep(250)
}
if (!loginPageReady) throw new Error(`Platform login page did not finish loading: ${JSON.stringify(lastLoginPageState)}`)

const login = await evaluate(`(async () => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(${JSON.stringify({ username, password })})
  })
  const payload = await response.json()
  if (!response.ok || !payload?.data?.token) throw new Error('Browser login failed')
  const data = payload.data
  localStorage.setItem('xiqin_token', data.token)
  localStorage.setItem('xiqin_user', JSON.stringify({
    id: data.userId,
    username: data.username,
    email: data.email,
    phone: '',
    avatar: data.avatar,
    roleName: data.roleName,
    roleCode: data.roleCode,
    roleId: 0,
    status: 1,
    permissions: data.permissions || []
  }))
  return data.username
})()`, true)

await send('Page.navigate', { url: `${platformUrl}/projects/2` })

let clicked = false
for (let attempt = 0; attempt < 60; attempt += 1) {
  clicked = await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')]
      .find((element) => element.textContent.trim() === '查看')
    if (button) {
      button.click()
      return true
    }
    const tab = [...document.querySelectorAll('.el-tabs__item')]
      .find((element) => element.textContent.includes('项目资料'))
    if (!tab) return false
    tab.click()
    return false
  })()`)
  if (clicked) break
  await sleep(500)
}
if (!clicked) {
  const pageState = await evaluate(`({
    href: location.href,
    title: document.title,
    text: document.body?.innerText?.slice(0, 1200) || ''
  })`)
  throw new Error(`Could not find the Office document view button: ${JSON.stringify(pageState)}`)
}

let result
for (let attempt = 0; attempt < 240; attempt += 1) {
  result = await evaluate(`(() => ({
    ready: window.__ooReady === true,
    officeError: window.__officeError || null,
    onlyOfficeError: window.__ooError ? JSON.stringify(window.__ooError) : null,
    alert: document.querySelector('.editor-dialog .el-alert__title')?.textContent || null,
    iframeCount: document.querySelectorAll('#office-editor-container iframe').length
  }))()`)
  if (result.ready || result.officeError || result.onlyOfficeError || result.alert) break
  await sleep(500)
}

socket.close()
if (!result?.ready) throw new Error(`OnlyOffice did not become ready: ${JSON.stringify(result)}`)
console.log(JSON.stringify({ login, ready: result.ready, iframeCount: result.iframeCount }))
