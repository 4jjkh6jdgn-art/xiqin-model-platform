import { createCanvas, Image } from 'canvas';

// 模拟 converter.js 的 document polyfill
const createImage = () => {
  const image = new Image();
  image.addEventListener = (event, handler) => {
    if (event === 'load') image.onload = handler;
    if (event === 'error') image.onerror = handler;
  };
  image.removeEventListener = (event, handler) => {
    if (event === 'load' && image.onload === handler) image.onload = null;
    if (event === 'error' && image.onerror === handler) image.onerror = null;
  };
  return image;
};
globalThis.document = {
  createElement: (name) => {
    if (name === 'canvas') return createCanvas(1, 1);
    if (name === 'img') return createImage();
    return { style: {} };
  },
  createElementNS: (_ns, name) => {
    if (name === 'canvas') return createCanvas(1, 1);
    if (name === 'img') return createImage();
    return { style: {} };
  },
};

const { createElementNS } = await import('three/src/utils.js');
const img = createElementNS('img');
console.log('img created:', img.constructor.name);
img.addEventListener('load', function() {
  console.log('load event, this.width=', this.width, 'this.height=', this.height);
});
img.addEventListener('error', function(e) {
  console.log('error event', e);
});
img.src = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
setTimeout(() => console.log('after 200ms: width=', img.width, 'height=', img.height, 'complete=', img.complete), 200);
