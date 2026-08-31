import amqp from 'amqplib';
import { Client as MinioClient } from 'minio';

const RABBITMQ_URL = process.env.RABBITMQ_URL
  || `amqp://${encodeURIComponent(process.env.RABBITMQ_USER || 'xiqin')}:${encodeURIComponent(process.env.RABBITMQ_PASSWORD || 'xiqin_mq_2024')}@${process.env.RABBITMQ_HOST || 'rabbitmq'}:${process.env.RABBITMQ_PORT || '5672'}`;
const QUEUE_NAME = process.env.MODEL_PROCESS_QUEUE || 'xiqin.model.process';
const MINIO_ENDPOINT = process.env.MINIO_ENDPOINT || 'http://minio:9000';
const MINIO_ACCESS_KEY = process.env.MINIO_ACCESS_KEY || 'xiqin';
const MINIO_SECRET_KEY = process.env.MINIO_SECRET_KEY || 'xiqin_minio_2024';

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

async function listModelFiles(bucket, prefix) {
  const objects = [];
  const stream = minioClient.listObjects(bucket, prefix, true);
  for await (const obj of stream) {
    objects.push(obj);
  }
  return objects;
}

function detectFileType(fileName) {
  const ext = (fileName || '').split('.').pop().toLowerCase();
  if (['fbx', 'obj', 'gltf', 'glb', 'stl', 'ply', 'dae'].includes(ext)) return 'display';
  if (['png', 'jpg', 'jpeg', 'tga', 'bmp', 'tiff', 'tif', 'exr', 'hdr', 'webp'].includes(ext)) {
    const base = fileName.replace(/\.[^.]+$/, '');
    if (/(?:^|[_ .-])(BASE[ _-]?COLOR|BASECOLOR|DIFFUSE|ALBEDO|COLOR|NORMAL|NORMALGL|NORMALDX|BUMP|METALLIC|METALNESS|METAL|ROUGHNESS|ROUGH|AO|OCCLUSION|AMBIENT|EMISSIVE|EMISSION|OPACITY|ALPHA|DISPLACEMENT|HEIGHT|DISP|D|N|M|R|E|A)$/i.test(base)) return 'texture';
    if (/(截图|效果图|预览|缩略图|渲染图|参考图|screenshot|preview|thumbnail|render|reference)/i.test(base)) return 'reference';
    return 'texture';
  }
  return 'other';
}

async function reprocessModel(modelId, versionNum = 1, userId = 1) {
  const bucket = 'models';
  const prefix = `models/${modelId}/versions/v${versionNum}/`;
  const objects = await listModelFiles(bucket, prefix);
  const files = objects.map((obj) => {
    const fileName = obj.name.split('/').pop();
    return {
      id: 0,
      fileName,
      fileType: detectFileType(fileName),
      fileFormat: (fileName.split('.').pop() || '').toLowerCase(),
      s3Key: obj.name,
    };
  });

  const connection = await amqp.connect(RABBITMQ_URL);
  const channel = await connection.createChannel();
  await channel.assertQueue(QUEUE_NAME, { durable: true });
  const message = {
    modelId,
    versionNum,
    userId,
    bucket,
    prefix,
    files,
  };
  channel.sendToQueue(QUEUE_NAME, Buffer.from(JSON.stringify(message)), { persistent: true });
  console.log(`已发送重新处理任务 modelId=${modelId} files=${files.length}`);
  await channel.close();
  await connection.close();
}

const modelId = parseInt(process.argv[2], 10);
if (!modelId) {
  console.error('用法: node reprocess-models.js <modelId> [versionNum] [userId]');
  process.exit(1);
}
const versionNum = parseInt(process.argv[3] || '1', 10);
const userId = parseInt(process.argv[4] || '1', 10);
reprocessModel(modelId, versionNum, userId).catch((err) => {
  console.error('发送失败:', err);
  process.exit(1);
});
