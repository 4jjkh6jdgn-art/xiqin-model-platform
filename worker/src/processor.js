/**
 * 模型处理流水线
 *
 * 1. 从 MinIO 下载模型文件
 * 2. 将 FBX 显示文件转换为 GLB
 * 3. 从首个可用模型生成缩略图
 * 4. 将转换结果和缩略图上传回 MinIO
 * 5. 回调后端接口更新模型状态
 */
import axios from 'axios';
import { Client as MinioClient } from 'minio';
import fs from 'fs';
import path from 'path';
import os from 'os';
import http from 'http';
import https from 'https';
import { execFile } from 'child_process';
import { promisify } from 'util';

import { convertFbxToGlb, generateThumbnail, generatePlaceholderThumbnail } from './converter.js';
import { detectFileType } from './fileDetector.js';

const execFileAsync = promisify(execFile);

const ARCHIVE_EXTENSIONS = new Set(['rar', 'zip', '7z']);
const MODEL_EXTENSIONS = new Set(['fbx', 'obj', 'gltf', 'glb', 'stl', 'ply', 'dae']);

const MINIO_ENDPOINT = process.env.MINIO_ENDPOINT || 'http://minio:9000';
const MINIO_ACCESS_KEY = process.env.MINIO_ACCESS_KEY || 'xiqin';
const MINIO_SECRET_KEY = process.env.MINIO_SECRET_KEY || 'xiqin_minio_2024';
const BACKEND_URL = process.env.BACKEND_URL || 'http://backend:8080';

/**
 * 解析 MinIO 端点配置
 */
function parseMinioEndpoint(endpoint) {
  const url = new URL(endpoint);
  return {
    endPoint: url.hostname,
    port: url.port ? parseInt(url.port, 10) : url.protocol === 'https:' ? 443 : 80,
    useSSL: url.protocol === 'https:',
  };
}

const minioConfig = parseMinioEndpoint(MINIO_ENDPOINT);
// 自定义传输层：包装http/https模块，为每个请求设置超长超时（10分钟）
// 避免大文件上传/下载时socket hang up
const baseTransport = minioConfig.useSSL ? https : http;
const customTransport = {
  ...baseTransport,
  request: (options, callback) => {
    const req = baseTransport.request(options, callback);
    // 设置socket超时：10分钟，大文件上传/下载需要足够时间
    req.setTimeout(600000, () => {
      req.destroy(new Error('请求超时（10分钟）'));
    });
    return req;
  },
};
const minioClient = new MinioClient({
  endPoint: minioConfig.endPoint,
  port: minioConfig.port,
  useSSL: minioConfig.useSSL,
  accessKey: MINIO_ACCESS_KEY,
  secretKey: MINIO_SECRET_KEY,
  transport: customTransport,
});

/**
 * 从 MinIO 下载对象为 Buffer
 * @param {string} bucket
 * @param {string} objectKey
 * @returns {Promise<Buffer>}
 */
async function downloadObject(bucket, objectKey) {
  const stream = await minioClient.getObject(bucket, objectKey);
  const chunks = [];
  for await (const chunk of stream) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

/**
 * 上传 Buffer 到 MinIO（带重试，大文件不易超时）
 * @param {string} bucket
 * @param {string} objectKey
 * @param {Buffer} data
 * @param {string} contentType
 */
async function uploadObject(bucket, objectKey, data, contentType) {
  const maxRetries = 3;
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      await minioClient.putObject(bucket, objectKey, data, data.length, {
        'Content-Type': contentType,
      });
      console.log(`[processor] 已上传: ${bucket}/${objectKey} (${(data.length / 1024 / 1024).toFixed(1)}MB)`);
      return;
    } catch (err) {
      console.warn(`[processor] 上传失败 (尝试 ${attempt}/${maxRetries}): ${objectKey} - ${err.message}`);
      if (attempt === maxRetries) throw err;
      await new Promise((r) => setTimeout(r, 2000 * attempt));
    }
  }
}

/**
 * 去除文件扩展名（保留文件名主体）
 * @param {string} filename
 */
function stripExtension(filename) {
  const dotIndex = filename.lastIndexOf('.');
  return dotIndex > 0 ? filename.slice(0, dotIndex) : filename;
}

/**
 * 判断是否为压缩包文件
 */
function isArchiveFile(fileName) {
  const ext = (fileName || '').split('.').pop().toLowerCase();
  return ARCHIVE_EXTENSIONS.has(ext);
}

/**
 * 解压压缩包到指定目录（使用 7z，支持 RAR/ZIP/7Z）
 */
async function extractArchive(archiveBuffer, archiveName, targetDir) {
  const archivePath = path.join(targetDir, archiveName);
  fs.writeFileSync(archivePath, archiveBuffer);
  try {
    await execFileAsync('7z', ['x', archivePath, `-o${targetDir}`, '-y'], {
      maxBuffer: 1024 * 1024 * 200,
    });
  } finally {
    try { fs.unlinkSync(archivePath); } catch (_) { /* ignore */ }
  }
}

/**
 * 递归扫描目录，找出所有模型文件（FBX/OBJ/GLB 等）
 * @returns {Array<{fileName: string, filePath: string}>}
 */
function findModelFiles(dir) {
  const results = [];
  function scan(currentDir) {
    let entries;
    try {
      entries = fs.readdirSync(currentDir, { withFileTypes: true });
    } catch (_) {
      return;
    }
    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        scan(fullPath);
      } else if (entry.isFile()) {
        const ext = entry.name.split('.').pop().toLowerCase();
        if (MODEL_EXTENSIONS.has(ext)) {
          results.push({ fileName: entry.name, filePath: fullPath });
        }
      }
    }
  }
  scan(dir);
  return results;
}

/**
 * 通知后端模型处理结果
 * @param {number} modelId
 * @param {{ status: string, versionNum?: number, thumbnailUrl?: string, convertedFileKey?: string, convertedFileFormat?: string }} payload
 */
async function notifyBackend(modelId, payload) {
  try {
    await axios.post(`${BACKEND_URL}/api/models/${modelId}/process-complete`, payload, {
      timeout: 15000,
      // 显式禁用代理：worker 容器可能残留 HTTP_PROXY 环境变量，
      // 若不禁用，axios 会经宿主代理访问内网 backend 导致 502
      proxy: false,
      headers: { 'Content-Type': 'application/json' },
    });
    console.log(`[processor] 已通知后端 modelId=${modelId} status=${payload.status}`);
  } catch (err) {
    console.error(
      `[processor] 通知后端失败 modelId=${modelId}:`,
      err.response?.data || err.message
    );
  }
}

/**
 * 处理单个模型任务
 * @param {object} message RabbitMQ 消息
 * @param {number} message.modelId
 * @param {number} [message.userId]
 * @param {string} message.bucket
 * @param {string} message.prefix
 * @param {Array<{id: number, fileName: string, fileType: string, fileFormat: string, s3Key: string}>} message.files
 */
export async function processModel(message) {
  const { modelId, versionNum = 1, userId, bucket, prefix, files = [] } = message;

  console.log(
    `[processor] 开始处理 modelId=${modelId} userId=${userId ?? '-'} bucket=${bucket} prefix=${prefix}`
  );

  // 对未标注 fileType 的文件做兜底自动检测
  const normalizedFiles = files.map((f) => ({
    ...f,
    fileType: f.fileType || detectFileType(f.fileName),
  }));

  // 找出可转换的 FBX 显示文件
  let fbxFiles = normalizedFiles.filter(
    (f) => f.fileType === 'display' && (f.fileFormat || '').toLowerCase() === 'fbx'
  );

  // 临时解压目录（压缩包内可能包含 FBX 模型）
  let tempExtractDir = null;

  // 如果没有直接上传的 FBX，检查是否有压缩包并解压
  if (fbxFiles.length === 0) {
    const archiveFiles = normalizedFiles.filter((f) => isArchiveFile(f.fileName));
    if (archiveFiles.length > 0) {
      tempExtractDir = fs.mkdtempSync(path.join(os.tmpdir(), 'model-extract-'));
      console.log(`[processor] 发现 ${archiveFiles.length} 个压缩包，解压到: ${tempExtractDir}`);
      for (const archive of archiveFiles) {
        console.log(`[processor] 下载并解压: ${bucket}/${archive.s3Key} (${archive.fileName})`);
        const archiveBuffer = await downloadObject(bucket, archive.s3Key);
        await extractArchive(archiveBuffer, archive.fileName, tempExtractDir);
      }
      const extractedModels = findModelFiles(tempExtractDir);
      console.log(`[processor] 压缩包内找到 ${extractedModels.length} 个模型文件: ${extractedModels.map((m) => m.fileName).join(', ')}`);
      // 将解压出的 FBX 转为统一格式，buffer 字段存磁盘路径供后续读取
      fbxFiles = extractedModels
        .filter((m) => m.fileName.toLowerCase().endsWith('.fbx'))
        .map((m) => ({
          fileName: m.fileName,
          fileType: 'display',
          fileFormat: 'fbx',
          s3Key: null,
          extractedPath: m.filePath,
        }));
    }
  }

  if (fbxFiles.length === 0) {
    if (tempExtractDir) {
      try { fs.rmSync(tempExtractDir, { recursive: true, force: true }); } catch (_) { /* ignore */ }
    }
    throw new Error(`modelId=${modelId} 未找到 FBX 显示文件（包括压缩包内）`);
  }

  let convertedFileKey = null;
  let firstGlbBuffer = null;
  let tempTextureDir = null;

  try {
    // ---- 0. 下载同模型的外部贴图，供 FBX->GLB 转换时嵌入 ----
    const textureFiles = normalizedFiles.filter((f) => f.fileType === 'texture');
    let downloadedTextures = [];
    if (textureFiles.length > 0) {
      tempTextureDir = fs.mkdtempSync(path.join(os.tmpdir(), 'model-textures-'));
      for (const tex of textureFiles) {
        try {
          const buffer = await downloadObject(bucket, tex.s3Key);
          const safeName = path.basename(tex.fileName);
          const filePath = path.join(tempTextureDir, safeName);
          fs.writeFileSync(filePath, buffer);
          downloadedTextures.push({ fileName: safeName, buffer });
          console.log(`[processor] 已下载贴图: ${tex.fileName} (${buffer.length} bytes)`);
        } catch (err) {
          console.warn(`[processor] 贴图下载失败 ${tex.fileName}:`, err.message);
        }
      }
    }

    // ---- 1. 转换所有 FBX 显示文件为 GLB ----
    for (const file of fbxFiles) {
      const fbxBuffer = file.extractedPath
        ? fs.readFileSync(file.extractedPath)
        : await downloadObject(bucket, file.s3Key);
      console.log(`[processor] 转换 FBX: ${file.fileName} (${fbxBuffer.length} bytes)`);

      const glbBuffer = await convertFbxToGlb(fbxBuffer, downloadedTextures);
      console.log(
        `[processor] FBX 转 GLB 成功: ${file.fileName} -> ${glbBuffer.length} bytes`
      );

      const baseName = stripExtension(file.fileName);
      const key = `${prefix}converted/${baseName}.glb`;
      await uploadObject(bucket, key, glbBuffer, 'model/gltf-binary');

      // 记录首个转换结果用于回调与缩略图生成
      if (!convertedFileKey) {
        convertedFileKey = key;
        firstGlbBuffer = glbBuffer;
      }
    }

    // ---- 2. 生成缩略图（复用首个转换出的 GLB；WebGL 渲染失败时降级为占位图） ----
    let thumbnailBuffer;
    try {
      thumbnailBuffer = await generateThumbnail(firstGlbBuffer);
      console.log(
        `[processor] 缩略图生成成功: ${thumbnailBuffer.length} bytes (512x512)`
      );
    } catch (thumbError) {
      console.warn(
        `[processor] WebGL 缩略图渲染失败，降级为占位图: ${thumbError.message}`
      );
      thumbnailBuffer = generatePlaceholderThumbnail(
        normalizedFiles.find((f) => f.fileType === 'display')?.fileName
      );
    }

    const thumbnailKey = `${prefix}thumbnail/${modelId}.png`;
    await uploadObject(bucket, thumbnailKey, thumbnailBuffer, 'image/png');

    // ---- 3. 通知后端处理成功（传 S3 key 而非内网 URL，由后端生成预签名 URL） ----
    await notifyBackend(modelId, {
      status: 'ready',
      versionNum,
      thumbnailKey,
      thumbnailBucket: bucket,
      convertedFileKey,
      convertedFileFormat: 'glb',
    });
  } catch (error) {
    console.error(`[processor] modelId=${modelId} 处理失败:`, error.message);

    // ---- 4. 通知后端处理失败 ----
    await notifyBackend(modelId, {
      status: 'error',
      versionNum,
      error: error.message,
    });

    throw error;
  } finally {
    if (tempExtractDir) {
      try { fs.rmSync(tempExtractDir, { recursive: true, force: true }); } catch (_) { /* ignore */ }
    }
    if (tempTextureDir) {
      try { fs.rmSync(tempTextureDir, { recursive: true, force: true }); } catch (_) { /* ignore */ }
    }
  }
}
