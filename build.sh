#!/usr/bin/env bash
set -Eeuo pipefail

readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly IMAGE_NAME="${IMAGE_NAME:-crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/synap-server}"
readonly IMAGE_TAG="${IMAGE_TAG:-latest}"
readonly DATE_TAG="${DATE_TAG:-$(date +%Y%m%d)}"
readonly PUSH_IMAGE="${PUSH_IMAGE:-false}"
readonly SKIP_TESTS="${SKIP_TESTS:-true}"
readonly BASE_IMAGE_REPOSITORY="${BASE_IMAGE_REPOSITORY:-crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/eclipse-temurin}"

command -v mvn >/dev/null || { echo "未找到 mvn" >&2; exit 1; }
command -v docker >/dev/null || { echo "未找到 docker" >&2; exit 1; }

normalize_arch() {
  case "$1" in
    amd64 | x86_64) echo "amd64" ;;
    arm64 | aarch64) echo "arm64" ;;
    *) echo "不支持的架构: $1" >&2; return 1 ;;
  esac
}

readonly IMAGE_ARCH="$(normalize_arch "${IMAGE_ARCH:-$(uname -m)}")"
readonly VERSION_TAG="${IMAGE_NAME}:${IMAGE_TAG}-${IMAGE_ARCH}"
readonly DATED_TAG="${IMAGE_NAME}:${DATE_TAG}-${IMAGE_ARCH}"
readonly BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
readonly VCS_REF="$(git -C "$PROJECT_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown)"

echo "========== 更新identify_config 配置=========="

rm -rf deploy/open_config/identify_config/*
cp src/main/java/com/coolxer/rule/* deploy/open_config/identify_config/

echo "========== 构建 synap-server (${IMAGE_ARCH}) =========="

maven_args=(clean package)
if [[ "$SKIP_TESTS" == "true" ]]; then
  maven_args+=(-DskipTests)
fi
mvn -f "$PROJECT_DIR/pom.xml" "${maven_args[@]}"

docker build \
  --platform "linux/${IMAGE_ARCH}" \
  --build-arg "BASE_IMAGE_REPOSITORY=${BASE_IMAGE_REPOSITORY}" \
  --build-arg "BASE_IMAGE_ARCH=${IMAGE_ARCH}" \
  --build-arg "BUILD_DATE=${BUILD_DATE}" \
  --build-arg "VCS_REF=${VCS_REF}" \
  --build-arg "VERSION=${IMAGE_TAG}" \
  --tag "$VERSION_TAG" \
  --tag "$DATED_TAG" \
  "$PROJECT_DIR"

echo "已构建: $VERSION_TAG"
echo "已构建: $DATED_TAG"

case "$PUSH_IMAGE" in
  true)
    docker push "$VERSION_TAG"
    docker push "$DATED_TAG"
    echo "镜像推送完成"
    ;;
  false)
    echo "跳过镜像推送 (PUSH_IMAGE=false)"
    ;;
  *)
    echo "PUSH_IMAGE 仅支持 true 或 false，当前值: $PUSH_IMAGE" >&2
    exit 1
    ;;
esac
