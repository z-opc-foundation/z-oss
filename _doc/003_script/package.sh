#!/bin/bash

git pull origin main
mvn clean

# ── 构建前置检查（卡 TASK-20261022-011 第 6 / 7 项）────────────────────
# ① 本地 m2 的 `.sha1` 边车必须与**实际字节**一致 —— 不一致 ⇒ 校验链断裂，
#    「拉中央」与「用本地 m2」解析同一坐标会拿到**不同制品**（本轮实测：拿到的是
#    带明文口令的那一份）⇒ **不符即失败**；
# ② 产物 jar 内不得有 classpath 根的 application.yml（库件把配置放 classpath 根
#    会**劫持宿主**：Spring Boot 的 classpath:/application.yml 只解析到第一个命中）。
#
# ⚠ 修复指引见脚本输出（边车：`precheck.sh --fix-sidecars`）。
# ⚠ 重建**必须 mvn clean**（本脚本已经 clean）：target/classes 里留着上一版的
#    陈旧配置，不 clean 会被打进新 jar（本轮实测踩过）。
PRECHECK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/precheck.sh"
"$PRECHECK" --m2-only || exit 1

mvn install -DskipTests=true

# 构建后再查一次产物（② 只有构建出 jar 才查得到）
"$PRECHECK" --jar-only || exit 1
