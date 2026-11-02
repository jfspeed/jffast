#!/usr/bin/env bash
# 首启播种：按文件名顺序把 /seed-sql/*.sql 灌进 MYSQL_DATABASE。
#
# 为什么不让官方 initdb 直接执行 .sql：
#   mysql:8.0 官方镜像里的 `mysql` 客户端默认字符集是 latin1（locale 为 POSIX/C 时尤为明确），
#   而 initdb 的 docker_process_sql 没有传 --default-character-set。UTF-8 的种子 SQL 会被
#   服务端按 latin1 -> utf8mb4 转码，中文菜单名落库即双重编码（"工作流管理" 变成
#   "å·¥ä½œæµ…"）。服务端的 --character-set-server=utf8mb4 只管服务端默认值，管不到
#   客户端发送侧；挂 [client] 配置在 Windows bind mount 下又常因 777 权限被 MySQL 忽略。
#   这里显式用 utf8mb4 客户端执行，是唯一与宿主机文件权限、locale 都无关的做法。
#
# 顺序 = docker-compose.yml 里 01–14 的文件名（与仓根 README「初始化数据库」清单逐行同序）。
# 由 docker-compose.yml 挂到 /docker-entrypoint-initdb.d/00-seed-sql.sh；
# 官方入口只在数据卷为空时执行 initdb，所以本脚本同样只跑一次。
(
  set -eu
  shopt -s nullglob

  files=(/seed-sql/*.sql)
  if [ "${#files[@]}" -eq 0 ]; then
    echo "[SEED][FAIL] /seed-sql 下没有任何 .sql 文件" >&2
    exit 1
  fi

  for file in "${files[@]}"; do
    echo "[SEED] $(basename "$file")"
    args=(-uroot)
    if [ -n "${MYSQL_DATABASE:-}" ]; then
      args+=("--database=$MYSQL_DATABASE")
    fi
    MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}" mysql \
      --protocol=socket --comments --default-character-set=utf8mb4 \
      "${args[@]}" < "$file"
  done

  echo "[SEED][OK] ${#files[@]} 个 SQL 文件已以 utf8mb4 播种"
)
