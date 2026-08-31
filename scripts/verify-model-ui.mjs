import { writeFile } from 'node:fs/promises'

const cdpPort = process.env.CDP_PORT || '9340'
const platformUrl = process.env.PLATFORM_URL || 'http://127.0.0.1:8088'
const username = process.env.XIQIN_TEST_USER
const password = process.env.XIQIN_TEST_PASSWORD
const modelId = process.env.XIQIN_TEST_MODEL_ID || '5'
const captureOutput = process.env.XIQIN_CAPTURE_OUTPUT
const capturePath = process.env.XIQIN_CAPTURE_PATH || '/dashboard'
const orbitDragX = Number(process.env.XIQIN_ORBIT_DRAG_X || 0)
const verifyQuickUploadClose = process.env.XIQIN_VERIFY_QUICK_UPLOAD_CLOSE === '1'
const verifyResourceList = process.env.XIQIN_VERIFY_RESOURCE_LIST === '1'
const verifyThumbnailFlow = process.env.XIQIN_VERIFY_THUMBNAIL_FLOW === '1'

if (!username || !password) throw new Error('Missing test credentials')
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
  || targets?.find((item) => item.type === 'page')
if (!target) throw new Error('Headless browser page target was not found')

const socket = new WebSocket(target.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  socket.addEventListener('open', resolve, { once: true })
  socket.addEventListener('error', reject, { once: true })
})

let nextId = 1
const pending = new Map()
const browserConsole = []
let quickUploadRequestBody = ''
socket.addEventListener('message', (event) => {
  const message = JSON.parse(event.data)
  if (message.method === 'Runtime.consoleAPICalled') {
    const values = (message.params?.args || []).map((arg) => arg.value ?? arg.description ?? '').join(' ')
    if (values.includes('[ModelViewer]')) browserConsole.push(values)
  }
  if (message.method === 'Fetch.requestPaused') {
    quickUploadRequestBody = message.params?.request?.postData || ''
    void send('Fetch.fulfillRequest', {
      requestId: message.params.requestId,
      responseCode: 200,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: Buffer.from(JSON.stringify({ code: 200, message: 'success', data: { id: 999999 } })).toString('base64')
    })
    return
  }
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
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text)
  }
  return response.result.value
}

await send('Page.enable')
await send('Runtime.enable')
if (verifyQuickUploadClose) {
  await send('Fetch.enable', {
    patterns: [{ urlPattern: '*/api/models/upload-folder*', requestStage: 'Request' }]
  })
}
await send('Emulation.setDeviceMetricsOverride', {
  width: 1366, height: 960, deviceScaleFactor: 1, mobile: false
})
await send('Page.navigate', { url: `${platformUrl}/login` })

for (let attempt = 0; attempt < 40; attempt += 1) {
  const ready = await evaluate(`location.href.startsWith(${JSON.stringify(platformUrl)}) && document.readyState === 'complete'`)
  if (ready) break
  await sleep(250)
}

await evaluate(`(async () => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(${JSON.stringify({ username, password })})
  })
  const payload = await response.json()
  if (!payload?.data?.token) {
    if (localStorage.getItem('xiqin_token')) return 'using-existing-session'
    throw new Error('Browser login failed')
  }
  const data = payload.data
  localStorage.setItem('xiqin_token', data.token)
  localStorage.setItem('xiqin_user', JSON.stringify({
    id: data.userId, username: data.username, email: data.email, phone: '',
    avatar: data.avatar, roleName: data.roleName, roleCode: data.roleCode,
    roleId: 0, status: 1, permissions: data.permissions || []
  }))
  return true
})()`, true)

await send('Page.navigate', { url: `${platformUrl}/models/${modelId}` })

let viewerState
for (let attempt = 0; attempt < 120; attempt += 1) {
  viewerState = await evaluate(`(() => ({
    href: location.href,
    info: document.querySelector('.viewer-container')?.dataset?.modelInfo || null,
    state: document.querySelector('.viewer-container')?.dataset?.modelState || null,
    error: document.querySelector('.viewer-error')?.textContent?.trim() || null,
    loading: Boolean(document.querySelector('.viewer-loading')),
    statusReady: [...document.querySelectorAll('.el-tag')].some((tag) => tag.textContent.trim() === '可用')
  }))()`)
  if (viewerState.error || (viewerState.state === 'ready' && viewerState.info && !viewerState.loading)) break
  await sleep(500)
}
if (viewerState?.error || !viewerState?.info) {
  throw new Error(`Model viewer did not load: ${JSON.stringify(viewerState)}`)
}

const modelUiState = await evaluate(`(async () => {
  const viewer = document.querySelector('.viewer-container')
  const infoButton = document.querySelector('.viewer-toolbar button[title="模型信息"]')
  const lightingButton = document.querySelector('.viewer-toolbar button[title="灯光设置"]')
  const fullscreenButton = document.querySelector('.viewer-toolbar button[title="全屏预览"]')
  const statsInitiallyHidden = !document.querySelector('.viewer-info')
  infoButton?.click()
  await new Promise((resolve) => setTimeout(resolve, 80))
  const statsVisibleAfterClick = Boolean(document.querySelector('.viewer-info'))
  infoButton?.click()
  await new Promise((resolve) => setTimeout(resolve, 80))
  lightingButton?.click()
  await new Promise((resolve) => setTimeout(resolve, 80))
  const lightingPanel = document.querySelector('.lighting-panel')
  lightingPanel?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
  await new Promise((resolve) => setTimeout(resolve, 80))
  const lightingStayedOpenAfterInsidePointer = Boolean(document.querySelector('.lighting-panel'))
  document.querySelector('.viewer-container canvas')?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }))
  await new Promise((resolve) => setTimeout(resolve, 80))
  const lightingClosedAfterOutsidePointer = !document.querySelector('.lighting-panel')
  fullscreenButton?.click()
  await new Promise((resolve) => setTimeout(resolve, 160))
  const fullscreenActiveAfterClick = document.fullscreenElement === viewer
  if (document.fullscreenElement) await document.exitFullscreen()
  const state = {
    gridVisibleByDefault: viewer?.dataset?.gridVisible,
    statsInitiallyHidden,
    statsVisibleAfterClick,
    lightingRows: lightingPanel?.querySelectorAll('.lp-row').length || 0,
    lightingPresets: lightingPanel?.querySelectorAll('.lp-presets button').length || 0,
    lightingText: lightingPanel?.textContent?.replace(/\s+/g, ' ').trim() || '',
    lightingStayedOpenAfterInsidePointer,
    lightingClosedAfterOutsidePointer,
    fullscreenButtonPresent: Boolean(fullscreenButton),
    fullscreenActiveAfterClick,
    toolbarActionText: document.querySelector('.viewer-toolbar')?.textContent?.replace(/\s+/g, ' ').trim() || '',
    previewHasOuterCard: Boolean(viewer?.closest('.el-card')),
    pageBackText: document.querySelector('.el-page-header__title')?.textContent?.trim() || '',
    duplicateReturnButtonCount: [...document.querySelectorAll('button')].filter((button) => button.textContent.trim() === '返回模型库').length,
    fileHeaderText: document.querySelector('.file-card .el-card__header')?.textContent?.replace(/\s+/g, ' ').trim() || '',
    downloadInsideFileHeader: Boolean(document.querySelector('.file-card .el-card__header .model-download')),
    thumbnailManagerPresent: Boolean(document.querySelector('.thumbnail-manager')),
    thumbnailOptionCount: document.querySelectorAll('.thumbnail-option').length,
    assetSummaryItems: document.querySelectorAll('.asset-summary-item').length,
    fileGroupText: [...document.querySelectorAll('.file-group-title')].map((node) => node.textContent.replace(/\s+/g, ' ').trim()).join(' | ')
  }
  return state
})()`, true)

const fullscreenRect = await evaluate(`(() => {
  const rect = document.querySelector('.viewer-toolbar button[title="全屏预览"]')?.getBoundingClientRect()
  return rect ? { x: rect.x, y: rect.y, width: rect.width, height: rect.height } : null
})()`)
if (fullscreenRect) {
  const x = fullscreenRect.x + fullscreenRect.width / 2
  const y = fullscreenRect.y + fullscreenRect.height / 2
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 })
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 })
  await sleep(250)
  modelUiState.fullscreenActiveWithPointer = await evaluate(`Boolean(document.fullscreenElement?.classList.contains('viewer-container'))`)
  await evaluate(`document.fullscreenElement ? document.exitFullscreen() : Promise.resolve()`, true)
} else {
  modelUiState.fullscreenActiveWithPointer = false
}

const layoutState = await evaluate(`(() => {
  const aside = document.querySelector('.sidebar')?.getBoundingClientRect()
  const user = document.querySelector('.sidebar-user')?.getBoundingClientRect()
  document.querySelector('.sidebar-user-trigger')?.click()
  return {
    sidebarUserAtBottom: Boolean(aside && user && Math.abs(aside.bottom - user.bottom) < 2),
    headerAvatarCount: document.querySelectorAll('.header .el-avatar').length,
    standaloneProfileItems: [...document.querySelectorAll('.el-menu-item')]
      .filter((item) => item.textContent.trim() === '个人设置').length,
    sidebarUserText: document.querySelector('.sidebar-user')?.textContent?.replace(/\s+/g, ' ').trim() || '',
    brandText: document.querySelector('.logo-text')?.textContent?.trim() || '',
    documentTitle: document.title
  }
})()`)
await sleep(500)
layoutState.dropdownText = await evaluate(`(() => [...document.querySelectorAll('.el-dropdown-menu')]
  .map((menu) => menu.textContent.replace(/\s+/g, ' ').trim()).filter(Boolean).join(' | '))()`)

let thumbnailFlowState = null
if (verifyThumbnailFlow) {
  for (let index = 0; index < 2; index += 1) {
    await evaluate(`(() => {
      const button = [...document.querySelectorAll('.viewer-toolbar button')]
        .find((item) => item.textContent.replace(/\s+/g, '').includes('设为缩略图'))
      if (!button) throw new Error('Thumbnail capture button not found')
      button.click()
    })()`)
    await sleep(1200)
  }
  const beforeSelection = await evaluate(`(() => ({
    count: document.querySelectorAll('.thumbnail-option').length,
    activeIndex: [...document.querySelectorAll('.thumbnail-option')].findIndex((item) => item.classList.contains('active'))
  }))()`)
  await evaluate(`(() => {
    const options = [...document.querySelectorAll('.thumbnail-option')]
    const target = options.find((item) => !item.classList.contains('active'))
    target?.click()
  })()`)
  await sleep(800)
  thumbnailFlowState = {
    ...beforeSelection,
    selectedCountAfterSwitch: await evaluate(`document.querySelectorAll('.thumbnail-option.active').length`),
    busyOverlayAfterSwitch: await evaluate(`document.querySelectorAll('.thumbnail-loading').length`)
  }
}

let quickUploadState = null
let resourceListState = null
if (verifyResourceList) {
  await send('Page.navigate', { url: `${platformUrl}/models` })
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const ready = await evaluate(`document.readyState === 'complete' && document.body.innerText.includes('模型库')`)
    if (ready) break
    await sleep(250)
  }
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')]
      .find((item) => item.textContent.replace(/\s+/g, '').includes('上传模型'))
    button?.click()
  })()`)
  await sleep(400)
  await evaluate(`(() => {
    const input = document.querySelector('.el-dialog input[webkitdirectory]')
    if (!input) throw new Error('Folder input not found')
    const specs = [
      ['玉璧资源/fbx/SM_YuBi.fbx', 1018],
      ['玉璧资源/tex/T_YuBi_D.png', 3300],
      ['玉璧资源/tex/T_YuBi_M.png', 615],
      ['玉璧资源/tex/T_YuBi_N.png', 2700],
      ['玉璧资源/screenshot/玉璧效果图.png', 1280],
      ['玉璧资源/sp/T_YuBi.spp', 7300],
      ['玉璧资源/package/SM_YuBi.unitypackage', 7600],
      ['玉璧资源/screenshot/Thumbs.db', 21],
      ['玉璧资源/Thumbs.db', 25]
    ]
    const transfer = new DataTransfer()
    specs.forEach(([path, size]) => {
      const name = path.split('/').pop()
      const file = new File([new Uint8Array(Number(size))], name)
      Object.defineProperty(file, 'webkitRelativePath', { value: path })
      transfer.items.add(file)
    })
    input.files = transfer.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })()`)
  await sleep(600)
  resourceListState = await evaluate(`(() => ({
    rows: document.querySelectorAll('.resource-row').length,
    header: document.querySelector('.resource-header')?.textContent?.replace(/\s+/g, ' ').trim() || '',
    firstPath: document.querySelector('.resource-path')?.textContent?.trim() || '',
    actions: document.querySelector('.resource-actions')?.textContent?.replace(/\s+/g, ' ').trim() || '',
    listScrollable: (() => {
      const list = document.querySelector('.resource-list')
      return Boolean(list && list.scrollHeight > list.clientHeight)
    })()
  }))()`)
}

if (verifyQuickUploadClose) {
  await send('Page.navigate', { url: `${platformUrl}/models` })
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const ready = await evaluate(`document.readyState === 'complete' && document.body.innerText.includes('模型库')`)
    if (ready) break
    await sleep(250)
  }
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')]
      .find((item) => item.textContent.replace(/\s+/g, '').includes('上传模型'))
    button?.click()
  })()`)
  await sleep(400)
  await evaluate(`(() => {
    const input = document.querySelector('.el-dialog input[type="file"]')
    if (!input) throw new Error('Quick upload file input not found')
    const transfer = new DataTransfer()
    transfer.items.add(new File([new Uint8Array([1])], 'SM_AutoCloseTest.glb', { type: 'model/gltf-binary' }))
    input.files = transfer.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })()`)
  await sleep(300)
  const summaryBeforeUpload = await evaluate(`document.querySelector('.detect-panel')?.textContent?.replace(/\s+/g, ' ').trim() || ''`)
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('.el-dialog button')]
      .find((item) => item.textContent.includes('开始上传'))
    button?.click()
  })()`)
  let dialogClosed = false
  for (let attempt = 0; attempt < 40; attempt += 1) {
    dialogClosed = await evaluate(`(() => {
      const dialog = document.querySelector('.el-dialog')
      return !dialog || dialog.getBoundingClientRect().width === 0 || getComputedStyle(dialog).display === 'none'
    })()`)
    if (dialogClosed) break
    await sleep(150)
  }
  quickUploadState = {
    dialogClosed,
    summaryBeforeUpload,
    requestContainedFileTypes: quickUploadRequestBody.includes('name="fileTypes"') && quickUploadRequestBody.includes('display'),
    successMessageVisible: await evaluate(`document.body.innerText.includes('上传成功，系统正在后台处理模型')`)
  }
  await send('Fetch.disable')
}

let captureState = null
let libraryUiState = null
if (captureOutput) {
  if (!verifyResourceList) await send('Page.navigate', { url: `${platformUrl}${capturePath}` })
  if (!verifyResourceList && /^\/models\/\d+/.test(capturePath)) {
    for (let attempt = 0; attempt < 80; attempt += 1) {
      const settled = await evaluate(`(() => ({
        canvas: Boolean(document.querySelector('.viewer-container canvas')),
        loading: Boolean(document.querySelector('.viewer-loading')),
        error: document.querySelector('.viewer-error')?.textContent?.trim() || null
      }))()`)
      if (settled.error || (settled.canvas && !settled.loading)) break
      await sleep(250)
    }
  } else if (!verifyResourceList) {
    for (let attempt = 0; attempt < 40; attempt += 1) {
      const settled = await evaluate(`document.readyState === 'complete' && document.body.innerText.trim().length > 0`)
      if (settled) break
      await sleep(250)
    }
    await sleep(800)
  }
  if (capturePath === '/models/upload') {
    await evaluate(`(() => {
      const input = document.querySelector('.el-input__inner')
      if (input) {
        input.value = '规则验收预览'
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
    })()`)
    await sleep(150)
    await evaluate(`(() => {
      const next = [...document.querySelectorAll('button')]
        .find((button) => button.textContent.includes('下一步'))
      next?.click()
    })()`)
    await sleep(800)
    await evaluate(`(() => {
      const input = document.querySelector('input[type="file"]')
      if (!input) return
      const transfer = new DataTransfer()
      ;[
        ['SM_RuleTest.glb', 'model/gltf-binary'],
        ['T_RuleTest_D.png', 'image/png'],
        ['T_RuleTest_N.png', 'image/png'],
        ['RuleTest_截图.png', 'image/png'],
        ['readme.txt', 'text/plain']
      ].forEach(([name, type]) => transfer.items.add(new File([new Uint8Array([1])], name, { type })))
      input.files = transfer.files
      input.dispatchEvent(new Event('change', { bubbles: true }))
    })()`)
    await sleep(800)
  }
  if (capturePath === '/models') {
    for (let attempt = 0; attempt < 60; attempt += 1) {
      const ready = await evaluate(`document.querySelectorAll('.model-card').length > 0 && !document.querySelector('.grid-wrap .el-loading-mask')`)
      if (ready) break
      await sleep(250)
    }
    libraryUiState = await evaluate(`(async () => {
      const request = async (path) => {
        const response = await fetch(path, { headers: { Authorization: 'Bearer ' + localStorage.getItem('xiqin_token') } })
        const payload = await response.json()
        if (!response.ok || payload.code !== 200) throw new Error(payload.message || 'Library API request failed')
        return payload.data
      }
      const stats = await request('/api/models/library-stats')
      const ascending = await request('/api/models?page=0&size=50&sortField=name&sortDirection=asc')
      const descending = await request('/api/models?page=0&size=50&sortField=name&sortDirection=desc')
      const projectCategoryId = Object.keys(stats.projectCategoryCounts || {}).find((id) => stats.projectCategoryCounts[id] > 0)
      const projectCategory = projectCategoryId
        ? await request('/api/models?page=0&size=50&projectCategoryId=' + projectCategoryId)
        : null
      return {
        sortButtons: [...document.querySelectorAll('.sort-controls button')].map((button) => button.textContent.replace(/\s+/g, ' ').trim()),
        categoryCountBadges: [...document.querySelectorAll('.category-count')].map((node) => node.textContent.trim()),
        adminSidebarStats: document.querySelector('.sidebar-platform-stats')?.textContent?.replace(/\s+/g, ' ').trim() || '',
        cards: document.querySelectorAll('.model-card').length,
        firstCardTags: [...document.querySelectorAll('.model-card:first-child .format-tag')].map((node) => node.textContent.trim()),
        firstCardFileCount: document.querySelector('.model-card:first-child .file-count')?.textContent?.trim() || '',
        firstCardProject: document.querySelector('.model-card:first-child .model-project')?.textContent?.trim() || '',
        firstCardCreator: document.querySelector('.model-card:first-child .creator')?.textContent?.trim() || '',
        firstCardDownload: document.querySelector('.model-card:first-child .thumb-metrics span:last-child')?.textContent?.trim() || '',
        totalModels: stats.totalModels,
        storageBytes: stats.totalStorageBytes,
        memberCount: stats.memberCount,
        downloadCount: stats.downloadCount,
        namesAscending: ascending.list.map((item) => item.name),
        namesDescending: descending.list.map((item) => item.name),
        projectCategoryExpected: projectCategoryId ? stats.projectCategoryCounts[projectCategoryId] : 0,
        projectCategoryActual: projectCategory?.total || 0
      }
    })()`, true)
  }
  captureState = await evaluate(`(() => ({
    href: location.href,
    title: document.title,
    bodyText: document.body.innerText.replace(/\s+/g, ' ').trim().slice(0, 1200),
    manualTypeSelectors: document.querySelectorAll('.kind-select').length
  }))()`)
  if (orbitDragX) {
    const rect = await evaluate(`(() => {
      const box = document.querySelector('.viewer-container canvas')?.getBoundingClientRect()
      return box ? { x: box.x, y: box.y, width: box.width, height: box.height } : null
    })()`)
    if (rect) {
      const startX = rect.x + rect.width / 2
      const startY = rect.y + rect.height / 2
      await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: startX, y: startY, button: 'left', clickCount: 1 })
      for (let step = 1; step <= 12; step += 1) {
        await send('Input.dispatchMouseEvent', {
          type: 'mouseMoved', x: startX + orbitDragX * step / 12, y: startY,
          button: 'left', buttons: 1
        })
      }
      await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: startX + orbitDragX, y: startY, button: 'left', clickCount: 1 })
      await sleep(800)
    }
  }
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  await writeFile(captureOutput, Buffer.from(screenshot.data, 'base64'))
}

socket.close()
console.log(JSON.stringify({ viewerState, modelUiState, layoutState, thumbnailFlowState, quickUploadState, resourceListState, libraryUiState, captureState, browserConsole }))
