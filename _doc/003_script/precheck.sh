#!/usr/bin/env bash
# ============================================================
# precheck.sh — 构建前置检查（卡 TASK-20261022-011 第 6 / 7 项）
#
# 两道机械可判的门禁：
#
#   ① **m2 校验链完整性**（第 6 项）
#      本地 `~/.m2` 里，本仓坐标的 `.sha1` 边车文件必须与**实际字节**的 sha1 一致。
#      不一致 ⇒ 校验链断裂：本地件是「本地 install 的那一份」，
#      边车却还写着**中央在架**那一份的哈希 ⇒ 任何校验都发现不了「它们不是同一份」。
#      ⇒ **不符即失败**（本轮实测：z-oss-api:1.0.4 的边车写的是中央值 1725551e…，
#        实际字节是本地 install 值 a7f80324… ⇒ 干净环境重建会拿到**带明文口令**的那一份）。
#      ⚠ 注意区分两件事：
#        · **本项**查的是「本地文件 ↔ 本地边车」是否自洽（**本地仓**的完整性）；
#        · 「中央在架 ↔ 本地」**是不是同一份字节**是**另一道门禁**：
#          `006_部署方案/010_发版收尾清单.md` §三·九「制品坐标唯一性门禁」。
#          两道都要跑，别互相替代。
#
#   ② **classpath 根配置**（第 7 项，jar 已构建时才查）
#      `z-oss-api` 的 jar 内**不允许**再有根级 application.yml / application.properties /
#      bootstrap.yml —— 库件把配置放 classpath 根会**劫持宿主**（Spring Boot 的
#      `classpath:/application.yml` 在 classpath 上只解析到第一个命中）。
#      本仓自己的配置已改名为 `z-oss-standalone.yml`（**不叫** application* ⇒ 默认位置不命中）。
#
# 用法：
#   ./_doc/003_script/precheck.sh                # ① + ②
#   ./_doc/003_script/precheck.sh --m2-only      # 只跑 ①（构建前用）
#   ./_doc/003_script/precheck.sh --jar-only     # 只跑 ②（构建后用）
#   ./_doc/003_script/precheck.sh --fix-sidecars # 把「边车 ≠ 实际」的边车改写成实际值（修复 ①）
#   M2_REPO=/path/to/.m2/repository ./_doc/003_script/precheck.sh   # 指定 m2 仓库
#
# 退出码：0 全绿 / 1 有 FAIL
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
while [ ! -f "$REPO_ROOT/pom.xml" ] && [ "$REPO_ROOT" != "/" ]; do
    REPO_ROOT="$(dirname "$REPO_ROOT")"
done
[ -f "$REPO_ROOT/pom.xml" ] || { echo "[precheck] 找不到仓根 pom.xml"; exit 1; }
M2_REPO="${M2_REPO:-${HOME}/.m2/repository}"

RUN_M2=1
RUN_JAR=1
FIX_SIDECARS=0
for arg in "$@"; do
    case "$arg" in
        --m2-only)      RUN_JAR=0 ;;
        --jar-only)     RUN_M2=0 ;;
        --fix-sidecars) FIX_SIDECARS=1 ;;
        -h|--help)      sed -n '2,40p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "[precheck] 未知参数：$arg"; exit 1 ;;
    esac
done

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[precheck]${NC} $1"; }
warn() { echo -e "${YELLOW}[precheck]${NC} $1"; }
err()  { echo -e "${RED}[precheck]${NC} $1"; FAIL=$((FAIL+1)); }
FAIL=0

sha1_of() {
    if command -v sha1sum >/dev/null 2>&1; then
        sha1sum "$1" | awk '{print $1}'
    else
        shasum -a 1 "$1" | awk '{print $1}'
    fi
}

cd "$REPO_ROOT"

# ---------- 本仓坐标清单（groupId + artifactId 列表）----------
SPEC=$(python3 - <<'PY'
import os
import xml.etree.ElementTree as ET
NS = '{http://maven.apache.org/POM/4.0.0}'
def t(e, tag):
    if e is None:
        return ''
    x = e.find(NS + tag)
    return (x.text or '').strip() if x is not None else ''
root = ET.parse('pom.xml').getroot()
group = t(root, 'groupId') or t(root.find(NS + 'parent'), 'groupId')
out = []
def scan(path):
    try:
        r = ET.parse(path).getroot()
    except Exception:
        return
    g = t(r, 'groupId') or group
    aid = t(r, 'artifactId')
    if aid:
        out.append((g, aid))
    for m in r.iter(NS + 'module'):
        p = os.path.join(os.path.dirname(path), (m.text or '').strip(), 'pom.xml')
        if os.path.isfile(p):
            scan(p)
scan('pom.xml')
for g, a in sorted(set(out)):
    if g:
        print(f"{g}\t{a}")
PY
)

# ---------- ① m2 边车 vs 实际字节 ----------
if [ "$RUN_M2" -eq 1 ]; then
    echo ""
    echo "──────── 1. m2 校验链完整性（.sha1 边车 == 实际 sha1）────────"
    if [ ! -d "$M2_REPO" ]; then
        warn "本机没有 ${M2_REPO} ⇒ 本项**未采集**（不是「通过」）"
    elif [ -z "$SPEC" ]; then
        err "未能从 pom.xml 解析出本仓坐标清单"
    else
        log "仓: $(basename "$REPO_ROOT")  m2 仓库: ${M2_REPO}"
        CHECKED=0
        FIXED=0
        while IFS=$'\t' read -r g a; do
            [ -z "$a" ] && continue
            gpath="${g//.//}"
            art_dir="${M2_REPO}/${gpath}/${a}"
            [ -d "$art_dir" ] || continue
            for ver_dir in "${art_dir}"/*/; do
                [ -d "${ver_dir}" ] || continue
                ver="$(basename "${ver_dir}")"
                for f in "${ver_dir}"*.jar "${ver_dir}"*.pom; do
                    [ -f "$f" ] || continue
                    side="${f}.sha1"
                    [ -f "$side" ] || continue
                    CHECKED=$((CHECKED+1))
                    says="$(tr -d '[:space:]' < "$side" | tr 'A-Z' 'a-z')"
                    actual="$(sha1_of "$f")"
                    if [ "$says" = "$actual" ]; then
                        log "✅ ${g}:${a}:${ver} ($(basename "$f")) 边车与实际一致"
                        continue
                    fi
                    if [ "$FIX_SIDECARS" -eq 1 ]; then
                        printf '%s\n' "$actual" > "$side"
                        FIXED=$((FIXED+1))
                        log "🔧 ${g}:${a}:${ver} ($(basename "$f")) 边车已改写为实际值 ${actual:0:12}"
                        continue
                    fi
                    err "🔴 ${g}:${a}:${ver} ($(basename "$f")) **边车与实际字节不符**"
                    echo "        边车说: ${says}"
                    echo "        实际是: ${actual}"
                    echo "        ⇒ 校验链断裂：本地件与中央件**不是同一份**，"
                    echo "          而任何 sha1 校验都发现不了 ⇒ 干净环境重建会拿到"
                    echo "          **不可预期**（本轮实测：带明文口令）的那一份。"
                    echo "        修复：./_doc/003_script/precheck.sh --fix-sidecars"
                    echo "              （把边车改写成实际字节的 sha1，让本地仓自洽）"
                    echo "        ⚠ 「中央在架 ↔ 本地」是不是同一份字节，是**另一道门禁**："
                    echo "           lead 仓 006_部署方案/010_发版收尾清单.md §三·九"
                done
            done
        done <<< "$SPEC"
        if [ "$CHECKED" -eq 0 ]; then
            warn "本机 m2 里没有本仓坐标的带边车构件 ⇒ 本项**未采集**（不是「通过」）"
        else
            log "① 共核 ${CHECKED} 个构件"
            [ "$FIXED" -gt 0 ] && log "① 已修复 ${FIXED} 个边车"
        fi
    fi
fi

# ---------- ② jar 内不得有 classpath 根的配置 ----------
if [ "$RUN_JAR" -eq 1 ]; then
    echo ""
    echo "──────── 2. classpath 根配置（库件不得劫持宿主）────────"
    JARS=$(ls z-oss-api/target/*.jar 2>/dev/null | grep -v -- "-sources.jar" | grep -v -- "-javadoc.jar" || true)
    if [ -z "$JARS" ]; then
        warn "z-oss-api/target 下还没有 jar ⇒ 本项**未采集**（构建后再跑一次）"
    else
        for jar in $JARS; do
            bad=$(unzip -l "$jar" 2>/dev/null \
                  | awk '{print $NF}' \
                  | grep -E '^(application|bootstrap)(-[^/]*)?\.(yml|yaml|properties)$' || true)
            if [ -n "$bad" ]; then
                err "🔴 $(basename "$jar") 里有 classpath 根配置：$(echo "$bad" | tr '\n' ' ')"
                echo "        ⇒ 库件把配置放 classpath 根会**劫持宿主**"
                echo "          （Spring Boot 的 classpath:/application.yml 只解析到第一个命中）"
                echo "        修复：配置改名成**不叫** application* 的文件（如 z-oss-standalone.yml），"
                echo "              并由启动类用 spring.config.additional-location 显式加载"
                echo "        ⚠ 重建**必须 mvn clean**：target/classes 里还留着上一版的"
                echo "          陈旧配置，不 clean 的话它会被打进新 jar"
            else
                log "✅ $(basename "$jar") 无 classpath 根配置"
            fi
        done
    fi
fi

echo ""
if [ "$FAIL" -eq 0 ]; then
    log "✅ 构建前置检查全绿"
    exit 0
else
    err "❌ ${FAIL} 项失败 ⇒ **不要发布**（先按上面的修复指引处理）"
    exit 1
fi
