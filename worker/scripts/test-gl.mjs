import gl from 'gl';
try {
  const ctx = gl(64, 64);
  console.log('gl context created:', !!ctx);
  if (ctx) {
    console.log('version:', ctx.getParameter(ctx.VERSION));
    console.log('vendor:', ctx.getParameter(ctx.VENDOR));
    console.log('renderer:', ctx.getParameter(ctx.RENDERER));
  }
} catch (e) {
  console.error('gl error:', e.message);
  console.error(e.stack);
}
