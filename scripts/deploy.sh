#!/usr/bin/env bash
set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${project_dir}/.env.production"
compose_file="${project_dir}/docker-compose.prod.yml"

if ! docker compose version >/dev/null 2>&1; then
  echo "错误：未检测到 Docker Compose v2（docker compose）。" >&2
  exit 1
fi

if [[ ! -f "${env_file}" ]]; then
  echo "错误：缺少 ${env_file}，请先复制 .env.production.example 并填写。" >&2
  exit 1
fi

if grep -Eq '=CHANGE_ME' "${env_file}"; then
  echo "错误：.env.production 中仍有 CHANGE_ME 占位值。" >&2
  exit 1
fi

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

"${compose[@]}" config --quiet
"${compose[@]}" build --pull
"${compose[@]}" up -d --remove-orphans --wait
"${compose[@]}" ps

app_port="$(sed -n 's/^APP_PORT=//p' "${env_file}" | tail -n 1)"
bind_ip="$(sed -n 's/^APP_BIND_IP=//p' "${env_file}" | tail -n 1)"
if [[ -z "${bind_ip}" || "${bind_ip}" == "0.0.0.0" ]]; then
  bind_ip="服务器局域网IP"
fi
echo "部署完成：http://${bind_ip}:${app_port:-80}/"
