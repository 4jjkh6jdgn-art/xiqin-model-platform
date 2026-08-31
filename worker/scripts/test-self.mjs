// 模拟 converter.js 的 self polyfill
globalThis.self = {
  requestAnimationFrame: () => 0,
  cancelAnimationFrame: () => {},
  URL: globalThis.URL,
};

// 在 ES module 中，self 应该解析为 globalThis.self
console.log('typeof self:', typeof self);
console.log('self.URL === globalThis.URL:', self.URL === globalThis.URL);

// 导入 three 看看 GLTFLoader 中的 self
const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
console.log('GLTFLoader loaded');

// GLTFLoader 内部使用 self
// 模拟 GLTFLoader 的行为
const URL2 = self.URL || self.webkitURL;
console.log('self.URL || self.webkitURL:', typeof URL2);
if (URL2) {
  console.log('URL2.createObjectURL:', typeof URL2.createObjectURL);
}
