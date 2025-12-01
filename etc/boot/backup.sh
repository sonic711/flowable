#!/bin/bash

cd $(dirname $0)
BASEDIR=$(pwd)
set -e

# 參數
APP_HOME=$1
INPUT_VERSION=$2
DEFAULT_VERSION=$(date +'%Y%m%d_%H%M%S')
N_VERSION_KEEP=3

# 設定路徑
BACKUP_DIR="${APP_HOME}/backup"
PREPARE_DIR="${APP_HOME}/prepared_build"

# 確認準備目錄存在
if [[ ! -d "$PREPARE_DIR" ]]; then
  echo "Error: 準備目錄 $PREPARE_DIR 不存在。"
  exit 1
fi

# 判斷用戶是否有傳入版本號
if [[ -n "$INPUT_VERSION" ]]; then
  # 若用戶提供版本號，檢查格式
  if [[ ! "$INPUT_VERSION" =~ ^[0-9A-Za-z_]+$ ]]; then
    echo "Invalid version format: $INPUT_VERSION"
    exit 1
  fi
  VERSION="$INPUT_VERSION"
else
  # 若用戶未提供版本號，則使用預設版本
  echo "Warning: No Given Version => Alternatively Using Version $DEFAULT_VERSION"
  VERSION="$DEFAULT_VERSION"
fi

echo "backup version = [$VERSION]"

# 確認新目錄名稱，如果已經存在，刪除原本的目錄
NEW_DIR="${BACKUP_DIR}/${VERSION}"
if [[ -d "$NEW_DIR" ]]; then
  echo "Directory $NEW_DIR already exists. Cleaning up."
  rm -rf "$NEW_DIR"
fi

echo "create or modify version.txt in [$PREPARE_DIR]"
echo "$VERSION" > "${PREPARE_DIR}/version.txt"

# backup
echo "backup [$PREPARE_DIR] to [$NEW_DIR]"
mkdir -p $NEW_DIR
cp -r $PREPARE_DIR/. $NEW_DIR

echo "Backup completed."

# 清理舊備份，只保留 N_VERSION_KEEP 個
echo "Cleaning up old versions ...  => Keep $N_VERSION_KEEP Versions"
ls -1dt ${BACKUP_DIR}/* | tail -n +$((N_VERSION_KEEP + 1)) | xargs rm -rf

echo "Cleaning up old versions completed."

echo "================== Available Backup Version ==================="
ls -1t ${BACKUP_DIR}
echo "==============================================================="
