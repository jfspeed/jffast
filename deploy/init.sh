#!/usr/bin/env bash
#
# deploy/init.sh —— 生成/体检 deploy/.env（后端栈），并打印一份「起手清单」
#
# 本仓与前端仓（jffast-ui）分别发布、各自自足；两栈通过外部网络 jffast-net 互通。
# 本脚本只准备**后端栈**的 .env。
#
# 做什么：
#   1. 从 deploy/.env.example 复制出 deploy/.env（已存在则只补缺，不覆盖）
#   2. 生成 RSA 密钥对：后端两行写本仓 .env；前端公钥自动同步到同一台机上
#      并列 clone 的前端仓（不在同机则打印出来手动抄）
#   3. 生成随机口令 —— JFFAST_INIT_PASSWORD / MYSQL_PASSWORD / REDIS_PASSWORD
#      （样例里的 test1234 会被自动换掉）
#   4. 用 docker compose config 验证生成的 .env 真能被编排接受
#   5. 打印访问地址、账号、数据卷落点与启动命令
#
# 用法：
#   bash deploy/init.sh                      # 常规：生成或补齐 deploy/.env
#   bash deploy/init.sh --force              # 全部重新生成（旧文件备份 .env.bak.<时间戳>）
#   ENV_FILE=/tmp/x bash deploy/init.sh      # 写到别处（自检/CI 用，不动 deploy/.env）
#
# 想自己定口令就别让它随机 —— 先 export，脚本沿用不再覆盖：
#   MYSQL_PASSWORD='xxx' REDIS_PASSWORD='yyy' JFFAST_INIT_PASSWORD='zzz' bash deploy/init.sh
# 端口要改同理：BACKEND_HOST_PORT=80 bash deploy/init.sh（写进 .env，之后可手改）
#
# ── 复用说明（同一套编排换环境时通常无需改它）──
# 项目专属的三处都是**探测/通配**取值的，改 .env 即可，不必改脚本：
#   · 共享网络名   ：读 .env 里任意 *_NETWORK 键（各项目自己的名字，不写死）
#   · 前端仓位置   ：默认同级 jffast-ui；找不到时自动探测同级含 VITE_RSA_PUBLIC_KEY 样例的仓；
#                    也可用 SIBLING_UI_REPO=xxx bash deploy/init.sh 指定
#   · 端口 / 容器名：读 .env 的 BACKEND_HOST_PORT 与 compose 的 container_name
#
# ⚠️ 生成结果含私钥与口令，**不要提交**（.gitignore 已忽略 deploy/.env）。

set -euo pipefail

usage() {
  sed -n '3,45p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
example_file="$root_dir/deploy/.env.example"
env_file="${ENV_FILE:-$root_dir/deploy/.env}"
compose_file="$root_dir/deploy/docker-compose.yml"
force=0

case "${1:-}" in
  --force) force=1 ;;
  -h|--help) usage; exit 0 ;;
  "") ;;
  *) echo "[FATAL] 未知参数：$1（可用：--force / --help）" >&2; exit 1 ;;
esac

[ -f "$example_file" ] || { echo "[FATAL] 找不到样例文件 $example_file" >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "[FATAL] 需要 openssl（生成密钥与随机口令）" >&2; exit 1; }

# 读指定 .env 的某个键（不做变量插值，值里有 $ 也原样取回；剥离行尾 \r ——
# Windows 检出的 example/手编文件常带 CRLF，不剥离会把 \r 拼进密钥值写坏它）
get_kv_from() {
  awk -v k="$2=" 'index($0, k) == 1 { v = substr($0, length(k) + 1); sub(/\r$/, "", v); print v; exit }' "$1"
}
get_kv() { get_kv_from "$env_file" "$1"; }

# 写指定 .env 的某个键：index 前缀匹配 + 整行重建，避免 sed 分隔符被 base64 里的 / 吃掉
set_kv_into() {
  local file="$1" key="$2" val="$3" tmp
  tmp="$(mktemp)"
  awk -v k="$key=" -v v="$val" '
    index($0, k) == 1 { print k v; seen = 1; next }
    { print }
    END { if (!seen) print k v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}
set_kv() { set_kv_into "$env_file" "$1" "$2"; }

# 随机口令：只取字母数字。$ 会被 compose 当变量插值，# 和引号会被 .env 解析吃掉。
rand_pw() {
  local pw i
  for i in 1 2 3 4 5; do
    pw="$(openssl rand -base64 32 | tr -cd 'A-Za-z0-9' | cut -c1-20)"
    if [ "${#pw}" -ge 16 ]; then printf '%s' "$pw"; return 0; fi
  done
  echo "[FATAL] 随机口令生成失败（openssl rand 输出被截空）" >&2; return 1
}

# 取值优先级：显式 export > .env 里已有的非默认值 > 新生成
pick_pw() {
  local key="$1" cur="$2" given
  given="$(printenv "$key" || true)"
  if [ -n "$given" ]; then printf '%s' "$given"; return 0; fi
  if [ -n "$cur" ] && [ "$cur" != "test1234" ]; then printf '%s' "$cur"; return 0; fi
  rand_pw
}

action=""
if [ ! -f "$env_file" ]; then
  # 复制的同时规范化换行：example 在 Windows 检出（autocrlf）下是 CRLF，
  # 原样带入会让读到的每个值尾带 \r，写坏的 RSA 密钥后端无法解析
  tr -d '\r' < "$example_file" > "$env_file"
  action="已生成"
elif [ "$force" = "1" ]; then
  backup="$env_file.bak.$(date '+%Y%m%d%H%M%S')"
  cp "$env_file" "$backup"
  tr -d '\r' < "$example_file" > "$env_file"
  action="已重新生成（旧文件备份到 $(basename "$backup")）"
else
  # 已存在的也顺手清一次 \r（可能是旧版脚本或手编带入的）
  tr -d '\r' < "$env_file" > "$env_file.tmp" && mv "$env_file.tmp" "$env_file"
  action="已存在，只补缺失项（要全部重来加 --force）"
fi

# ── RSA 密钥对：--force 或任一为空时整对重生成（半对半新必然登录失败）──
priv_cur="$(get_kv RSA_PRIVATE_KEY)"
pub_cur="$(get_kv RSA_PUBLIC_KEY)"
if [ "$force" = "1" ] || [ -z "$priv_cur" ] || [ -z "$pub_cur" ]; then
  rsa_out="$(bash "$root_dir/deploy/gen-rsa.sh")"
  priv="$(printf '%s\n' "$rsa_out" | awk -v k='RSA_PRIVATE_KEY=' 'index($0,k)==1{print substr($0,length(k)+1); exit}')"
  pub="$(printf '%s\n' "$rsa_out" | awk -v k='RSA_PUBLIC_KEY=' 'index($0,k)==1 && index($0,"VITE_")!=1{print substr($0,length(k)+1); exit}')"
  [ -n "$priv" ] && [ -n "$pub" ] || { echo "[FATAL] 解析 gen-rsa.sh 输出失败" >&2; exit 1; }
  set_kv RSA_PRIVATE_KEY "$priv"
  set_kv RSA_PUBLIC_KEY "$pub"
  rsa_state="新生成"
else
  pub="$pub_cur"
  rsa_state="沿用已有密钥对"
fi
chmod 600 "$env_file" 2>/dev/null || true

# ── 前端公钥同步：同级并列 clone 的前端仓 ──
# 前端镜像的 VITE_RSA_PUBLIC_KEY 必须与后端私钥同对（不成对 = 登录必失败），
# 所以在这里把公钥直接写过去，省掉手动复制这一步。
ui_repo="${SIBLING_UI_REPO:-jffast-ui}"
ui_deploy_dir=""

# 三种目录布局都要能命中：
#   ① 仓内子目录（前端目录在本仓里）
#   ② 同级（前后端两个目录并列放在同一个父目录下）
#   ③ 跨一级（后端 Maven 根目录比仓库根深一级的布局）
find_sibling_deploy() {
  local marker="$1" d cand
  for d in "$root_dir" "$(dirname "$root_dir")" "$(dirname "$(dirname "$root_dir")")"; do
    for cand in "$d"/*/deploy; do
      [ -f "$cand/.env.example" ] || continue
      grep -q "$marker" "$cand/.env.example" || continue
      printf '%s' "$cand"
      return 0
    done
  done
  return 1
}

# 先按指定/默认仓名找，找不到再按「样例里带 VITE_RSA_PUBLIC_KEY」探测
for d in "$root_dir" "$(dirname "$root_dir")" "$(dirname "$(dirname "$root_dir")")"; do
  if [ -d "$d/$ui_repo/deploy" ]; then ui_deploy_dir="$d/$ui_repo/deploy"; break; fi
done
if [ -z "$ui_deploy_dir" ]; then
  ui_deploy_dir="$(find_sibling_deploy '^VITE_RSA_PUBLIC_KEY=' || true)"
fi
if [ -n "$ui_deploy_dir" ]; then ui_repo="$(basename "$(dirname "$ui_deploy_dir")")"; fi
ui_env_file="${ui_deploy_dir:-}/.env"
if [ -d "$ui_deploy_dir" ]; then
  if [ ! -f "$ui_env_file" ] && [ -f "$ui_deploy_dir/.env.example" ]; then
    tr -d '\r' < "$ui_deploy_dir/.env.example" > "$ui_env_file"
  fi
  if [ -f "$ui_env_file" ]; then
    ui_pub="$(get_kv_from "$ui_env_file" VITE_RSA_PUBLIC_KEY)"
    if [ "$force" = "1" ] || [ -z "$ui_pub" ] || [ "$ui_pub" != "$pub" ]; then
      set_kv_into "$ui_env_file" VITE_RSA_PUBLIC_KEY "$pub"
      chmod 600 "$ui_env_file" 2>/dev/null || true
      ui_state="已同步到 $ui_repo/deploy/.env（前端无需再配密钥）"
    else
      ui_state="$ui_repo 已是同一对，未改动"
    fi
  else
    ui_state="$ui_repo 仓存在但缺 .env.example，公钥需手动抄：见下方【前端公钥】"
    print_pub=1
  fi
else
  ui_state="本机没有并列的前端仓 —— 部署前端时把下面【前端公钥】整行抄进它的 deploy/.env"
  ui_repo="前端仓"
  print_pub=1
fi

# ── 口令与端口 ──
init_pw="$(pick_pw JFFAST_INIT_PASSWORD "$(get_kv JFFAST_INIT_PASSWORD)")"
set_kv JFFAST_INIT_PASSWORD "$init_pw"
mysql_pw="$(pick_pw MYSQL_PASSWORD "$(get_kv MYSQL_PASSWORD)")"
set_kv MYSQL_PASSWORD "$mysql_pw"
redis_pw="$(pick_pw REDIS_PASSWORD "$(get_kv REDIS_PASSWORD)")"
set_kv REDIS_PASSWORD "$redis_pw"
[ -n "$(get_kv MYSQL_DB)" ] || set_kv MYSQL_DB "jffast"
if [ -n "${BACKEND_HOST_PORT:-}" ]; then set_kv BACKEND_HOST_PORT "$BACKEND_HOST_PORT"; fi
chmod 600 "$env_file" 2>/dev/null || true

# ── 编排接受性自检 ──
compose_check="跳过（本机没有可用的 docker 守护进程）"
# 只装了 docker 命令但守护进程没起（Docker Desktop 未启动 / WSL 未开集成）时不判失败，
# 本脚本的职责是准备 .env，编排校验属于加分项
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if docker compose -f "$compose_file" --env-file "$env_file" config --quiet 2>/dev/null; then
    compose_check="通过（编排里所有必填项都已填上）"
  else
    err="$(docker compose -f "$compose_file" --env-file "$env_file" config 2>&1 | tail -3)"
    echo "[FATAL] docker compose config 校验失败：$err" >&2
    exit 1
  fi
fi

# ── 起手清单 ──
# 共享网络名：通配读 *_NETWORK（键名由 .env 决定，本项目默认 JFFAST_NETWORK）
net_name="$(awk -F= '/_NETWORK=/{v=$2; sub(/\r$/,"",v); print v; exit}' "$env_file")"
[ -n "$net_name" ] || net_name="jffast-net"
be_host_port="$(get_kv BACKEND_HOST_PORT)"; [ -n "$be_host_port" ] || be_host_port=7200
my_host_port="$(get_kv MYSQL_HOST_PORT)"; [ -n "$my_host_port" ] || my_host_port=3306
rd_host_port="$(get_kv REDIS_HOST_PORT)"; [ -n "$rd_host_port" ] || rd_host_port=6379
db_name="$(get_kv MYSQL_DB)"
mysql_cn="$(awk '/container_name:/{v=$2; sub(/\r$/,"",v); print v; exit}' "$compose_file")"
[ -n "$mysql_cn" ] || mysql_cn="jffast-mysql"
project="$(awk '$1=="name:"{v=$2; sub(/\r$/,"",v); print v; exit}' "$compose_file")"
[ -n "$project" ] || project="$(basename "$(dirname "$compose_file")")"

echo
echo "══════════════════════════════════════════════════════════════"
echo " $env_file —— $action"
echo "   RSA 密钥：$rsa_state"
echo "   前端公钥：$ui_state"
echo "   compose 校验：$compose_check"
if [ -n "${print_pub:-}" ]; then
  echo "   VITE_RSA_PUBLIC_KEY=$pub"
fi
echo "══════════════════════════════════════════════════════════════"
echo "【启动】执行后就起来了（首次会在服务器上编译镜像，约 5~10 分钟）"
echo "  docker network create $net_name    # 与前端共享的网络（只需一次）"
echo "  docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build"
echo
echo "【访问】"
printf '  后端 API      http://%s:%s/jffast-api/   （编排里已绑 127.0.0.1，仅联调用）\n' "127.0.0.1" "$be_host_port"
echo  "  前端页面在${ui_repo}里部署：cd 那个仓 && bash deploy/init.sh && compose up"
echo
echo "【账号】"
echo  "  管理员        admin / $init_pw"
echo  "  ⚠ 这个口令只在**全新库第一次启动**时写入；库里 admin 口令已被改过（例如导入过既有 dump）就沿用库内的，注入值不生效。"
echo
echo "【数据与落点】"
printf '  MySQL        宿主机 127.0.0.1:%s · 容器内 mysql:3306 · 库 %s · 用户 root · 口令 %s\n' "$my_host_port" "$db_name" "$mysql_pw"
printf '  Redis        宿主机 127.0.0.1:%s · 容器内 redis:6379 · 口令 %s\n' "$rd_host_port" "$redis_pw"
echo  "  命名卷（数据都在这里，down -v 会删）"
echo  "    ${project}_mysql-data    -> /var/lib/mysql"
echo  "    ${project}_redis-data    -> /data"
echo  "    查看：docker volume inspect ${project}_mysql-data"
echo
echo "【首启自检】任一条不对就先 docker logs $mysql_cn | grep -i error"
echo "  docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps        # 3 个 Up/healthy"
echo '  # 下面这条要先 set -a; . deploy/.env; set +a 把口令带进当前 shell'
echo "  docker exec $mysql_cn mysql -uroot -p\"\$MYSQL_PASSWORD\" -N -e \"SHOW TABLES\" \"$db_name\" | grep -c sys_   # 0 或查不到 = 播种被跳过"
echo
echo "【升级】git pull && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build"
echo "  ⚠ 已存在的库不会重跑播种，结构变更要手动执行幂等迁移脚本。"
echo "【备份】docker exec $mysql_cn mysqldump -uroot -p\"\$MYSQL_PASSWORD\" --single-transaction \"$db_name\" > backup_\$(date +%F).sql"
echo "══════════════════════════════════════════════════════════════"
