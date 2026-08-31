#!/usr/bin/env bash
set -Eeuo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${project_dir}/.env.production"
compose_file="${project_dir}/docker-compose.prod.yml"
timestamp="$(date +%Y%m%d_%H%M%S)"
backup_dir="${project_dir}/backups/${timestamp}"

if [[ ! -f "${env_file}" ]]; then
  echo "错误：缺少 ${env_file}。" >&2
  exit 1
fi

mkdir -p "${backup_dir}"
compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

echo "正在备份 PostgreSQL..."
"${compose[@]}" exec -T postgres sh -ec \
  'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "${backup_dir}/postgres.dump"

echo "正在备份 MinIO 文件..."
docker run --rm \
  --network xiqin_internal \
  --env-file "${env_file}" \
  -v "${backup_dir}:/backup" \
  --entrypoint /bin/sh \
  minio/mc:latest \
  -ec '
    mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    mc mirror --overwrite local/models /backup/minio/models
    mc mirror --overwrite local/thumbnails /backup/minio/thumbnails
    mc mirror --overwrite local/avatars /backup/minio/avatars
  '

(
  cd "${backup_dir}"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

echo "备份完成：${backup_dir}"
