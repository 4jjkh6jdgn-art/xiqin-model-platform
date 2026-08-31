import { generateThumbnail, generatePlaceholderThumbnail } from '../src/converter.js';
import { Client as MinioClient } from 'minio';
import axios from 'axios';

const MINIO_ENDPOINT = process.env.MINIO_ENDPOINT || 'http://minio:9000';
const MINIO_ACCESS_KEY = process.env.MINIO_ACCESS_KEY || 'xiqin';
const MINIO_SECRET_KEY = process.env.MINIO_SECRET_KEY || 'xiqin_minio_2024';
const BACKEND_URL = process.env.BACKEND_URL || 'http://backend:8080';

function parseMinioEndpoint(endpoint) {
  const url = new URL(endpoint);
  return {
    endPoint: url.hostname,
    port: url.port ? parseInt(url.port, 10) : url.protocol === 'https:' ? 443 : 80,
    useSSL: url.protocol === 'https:',
  };
}

const minioConfig = parseMinioEndpoint(MINIO_ENDPOINT);
const minioClient = new MinioClient({
  endPoint: minioConfig.endPoint,
  port: minioConfig.port,
  useSSL: minioConfig.useSSL,
  accessKey: MINIO_ACCESS_KEY,
  secretKey: MINIO_SECRET_KEY,
});

async function downloadObject(bucket, objectKey) {
  const stream = await minioClient.getObject(bucket, objectKey);
  const chunks = [];
  for await (const chunk of stream) chunks.push(chunk);
  return Buffer.concat(chunks);
}

async function uploadObject(bucket, objectKey, data, contentType) {
  await minioClient.putObject(bucket, objectKey, data, data.length, { 'Content-Type': contentType });
  console.log(`已上传: ${bucket}/${objectKey} (${data.length} bytes)`);
}

async function regenerate(modelId, versionNum = 1) {
  const bucket = 'models';
  const prefix = `models/${modelId}/versions/v${versionNum}/`;
  const glbKey = `${prefix}converted/SM_YaoChe.glb`; // TODO: 从 MinIO list 找到实际 glb
  // 先 list 找到 glb
  const stream = minioClient.listObjects(bucket, `${prefix}converted/`, true);
  let glbObj = null;
  for await (const obj of stream) {
    if (obj.name.endsWith('.glb')) { glbObj = obj; break; }
  }
  if (!glbObj) throw new Error('未找到 GLB');
  console.log('下载 GLB:', glbObj.name);
  const glbBuffer = await downloadObject(bucket, glbObj.name);
  console.log('生成缩略图...');
  let thumbnailBuffer;
  try {
    thumbnailBuffer = await generateThumbnail(glbBuffer);
  } catch (e) {
    console.warn('WebGL 失败，使用占位图:', e.message);
    thumbnailBuffer = generatePlaceholderThumbnail(`model-${modelId}`);
  }
  const thumbnailKey = `${prefix}thumbnail/${modelId}.png`;
  await uploadObject(bucket, thumbnailKey, thumbnailBuffer, 'image/png');
  // 通知后端
  await axios.post(`${BACKEND_URL}/api/models/${modelId}/process-complete`, {
    status: 'ready',
    versionNum,
    thumbnailKey,
    thumbnailBucket: bucket,
  }, { timeout: 15000, proxy: false, headers: { 'Content-Type': 'application/json' } });
  console.log('完成');
}

const modelId = parseInt(process.argv[2], 10);
if (!modelId) { console.error('用法: node regenerate-thumbnail.mjs <modelId> [versionNum]'); process.exit(1); }
regenerate(modelId, parseInt(process.argv[3] || '1', 10)).catch((e) => { console.error(e); process.exit(1); });
