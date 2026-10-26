#!/usr/bin/env bash
#
# 生成 jffast 登录加密用的 RSA 密钥对，输出可直接粘贴的 .env 片段。
#
# 为什么需要它：登录密码由前端用公钥加密、后端用私钥解密，
# **两边必须是同一对**。密钥不匹配时服务能正常启动，只有登录必失败，很难定位。
# 本脚本一次生成、同时给出后端两行和前端一行。
#
# 用法（Linux / macOS / Windows 的 Git Bash 或 WSL）：
#   bash deploy/gen-rsa.sh            # 打印到屏幕
#   bash deploy/gen-rsa.sh >> .env    # 追加到后端 .env
#
# Windows 没有 openssl 时，用项目自带的 Java 工具（格式由 RSAUtil 保证）：
#   java -cp ... cn.jfspeed.common.security.util.RsaKeyGenerator
#
# ⚠️ 生成结果不要提交进仓库。公钥泄露影响有限，私钥泄露等于所有人的登录密码可被解密。

set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
  echo "[FATAL] 未找到 openssl。请安装 openssl，或改用 cn.jfspeed.common.security.util.RsaKeyGenerator" >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

# PKCS#8 PEM，与 RSAUtil.generateKey() 内部用的密钥编码一致
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp_dir/key.pem" 2>/dev/null

# 私钥：Base64(PKCS#8 DER) —— 对应 RSAUtil.generateKey() 的 Base64(PrivateKey.getEncoded())
private_key="$(openssl pkcs8 -topk8 -nocrypt -in "$tmp_dir/key.pem" -outform DER | base64 | tr -d '\n')"

# 公钥：Base64(X.509 SubjectPublicKeyInfo DER) —— 前端 jsencrypt 直接消费这个格式，
# 与 CI 里 `openssl rsa -pubout -outform DER | base64` 的口径一致
public_key="$(openssl rsa -in "$tmp_dir/key.pem" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n')"

cat <<EOF
# ===== 生成时间: $(date '+%Y-%m-%d %H:%M:%S') =====

# --- 写入 jffast/deploy/.env（后端）---
RSA_PRIVATE_KEY=${private_key}
RSA_PUBLIC_KEY=${public_key}

# --- 写入 jffast-ui/deploy/.env（前端；必须是上面同一对的公钥）---
VITE_RSA_PUBLIC_KEY=${public_key}
EOF
