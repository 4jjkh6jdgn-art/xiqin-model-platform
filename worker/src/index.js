import amqp from 'amqplib';

import { processModel } from './processor.js';

const rabbitmqUser = process.env.RABBITMQ_USER || 'xiqin';
const rabbitmqPassword = process.env.RABBITMQ_PASSWORD || 'xiqin_mq_2024';
const rabbitmqHost = process.env.RABBITMQ_HOST || 'rabbitmq';
const rabbitmqPort = process.env.RABBITMQ_PORT || '5672';
const RABBITMQ_URL = process.env.RABBITMQ_URL
  || `amqp://${encodeURIComponent(rabbitmqUser)}:${encodeURIComponent(rabbitmqPassword)}@${rabbitmqHost}:${rabbitmqPort}`;
const QUEUE_NAME = process.env.MODEL_PROCESS_QUEUE || 'xiqin.model.process';

const INITIAL_RECONNECT_DELAY_MS = 1000;
const MAX_RECONNECT_DELAY_MS = 30000;

let reconnectAttempts = 0;
let connection = null;
let channel = null;
let reconnectTimer = null;

/**
 * 消费队列中的模型处理消息
 */
async function startWorker() {
  try {
    console.log(`[worker] 连接 RabbitMQ ${rabbitmqHost}:${rabbitmqPort} ...`);
    connection = await amqp.connect(RABBITMQ_URL);
    reconnectAttempts = 0;

    connection.on('error', (err) => {
      console.error('[worker] RabbitMQ 连接错误:', err.message);
    });

    connection.on('close', () => {
      console.warn('[worker] RabbitMQ 连接已关闭，准备重连...');
      scheduleReconnect();
    });

    channel = await connection.createChannel();

    // 队列需持久化，与生产者端 app.rabbitmq.queue-model-process 配置一致
    await channel.assertQueue(QUEUE_NAME, { durable: true });

    // 一次只处理一条消息，防止堆积
    await channel.prefetch(1);

    await channel.consume(
      QUEUE_NAME,
      async (msg) => {
        if (!msg) return;

        let parsed;
        try {
          parsed = JSON.parse(msg.content.toString());
        } catch (err) {
          console.error('[worker] 消息 JSON 解析失败，丢弃消息:', err.message);
          channel.nack(msg, false, false);
          return;
        }

        console.log(
          `[worker] 收到任务 modelId=${parsed.modelId} bucket=${parsed.bucket} prefix=${parsed.prefix} files=${parsed.files?.length ?? 0}`
        );

        try {
          await processModel(parsed);
          channel.ack(msg);
          console.log(`[worker] 模型 ${parsed.modelId} 处理完成`);
        } catch (err) {
          console.error(`[worker] 模型 ${parsed.modelId} 处理失败:`, err.message);
          // processModel 内部已向后端上报 status=error，此处丢弃消息避免死循环
          channel.nack(msg, false, false);
        }
      },
      { noAck: false }
    );

    console.log(`[worker] 已启动，监听队列: ${QUEUE_NAME}`);
  } catch (err) {
    console.error('[worker] 启动失败:', err.message);
    scheduleReconnect();
  }
}

/**
 * 指数退避重连
 */
function scheduleReconnect() {
  if (reconnectTimer) return;

  const delay = Math.min(
    INITIAL_RECONNECT_DELAY_MS * Math.pow(2, reconnectAttempts),
    MAX_RECONNECT_DELAY_MS
  );
  reconnectAttempts += 1;

  console.log(`[worker] ${delay / 1000}s 后重连（第 ${reconnectAttempts} 次尝试）...`);

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    startWorker();
  }, delay);
}

/**
 * 优雅关闭
 */
async function shutdown(signal) {
  console.log(`[worker] 收到 ${signal}，正在优雅关闭...`);
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  try {
    if (channel) await channel.close();
    if (connection) await connection.close();
  } catch (err) {
    // 忽略关闭过程中的错误
  }
  process.exit(0);
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

startWorker();
