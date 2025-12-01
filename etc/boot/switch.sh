#!/bin/bash

cd $(dirname $0)
BASEDIR=$(pwd)
set -e

# 參數
APP_HOME=$1
INPUT_VERSION=$2

# 設定路徑
BACKUP_DIR="${APP_HOME}/backup"
PREPARE_DIR="${APP_HOME}/prepared_build"

# 版本 new 則代表套用 prepare_build folder
VERSIONS=("new")

# 若 BACKUP_DIR 存在, 新增 BACKUP_DIR 中所有目錄之版本
if [ -d "$BACKUP_DIR" ]; then
  VERSIONS+=($(ls -1t "${BACKUP_DIR}"))
fi

# 如果用戶有傳入版本號，直接使用該版本
if [[ -n "$INPUT_VERSION" ]]; then
  if [[ ! " ${VERSIONS[@]} " =~ " ${INPUT_VERSION} " ]]; then
    echo "Error: 無效的版本號。請輸入有效的版本號。"
    exit 1
  fi
  SELECTED_VERSION="$INPUT_VERSION"
else
  # 如果用戶未提供版本號，則列出版本供用戶選擇
  echo "請選擇要套用的版本："
  for i in "${!VERSIONS[@]}"; do
    echo "$((i + 1))) ${VERSIONS[$i]}"
  done
  read -p "輸入版本號或選擇對應的數字： " USER_INPUT

  # 若用戶選擇數字，則轉換為對應版本
  if [[ "$USER_INPUT" =~ ^[0-9]+$ ]] && (( USER_INPUT >= 1 && USER_INPUT <= ${#VERSIONS[@]} )); then
    SELECTED_VERSION="${VERSIONS[$((USER_INPUT - 1))]}"
  else
    SELECTED_VERSION="$USER_INPUT"
  fi

  # 驗證選擇的版本是否有效
  if [[ ! " ${VERSIONS[@]} " =~ " ${SELECTED_VERSION} " ]]; then
    echo "Error: 無效的版本號。請選擇有效的版本。"
    exit 1
  fi
fi

# 根據選擇的版本號取得目錄
if [ "$SELECTED_VERSION" = "new" ]; then
  SELECTED_DIR="${PREPARE_DIR}"
else
  SELECTED_DIR="${BACKUP_DIR}/${SELECTED_VERSION}"
fi

# 驗證選擇的版本是否有效
if [[ ! -d "$SELECTED_DIR" ]]; then
  echo "Error: 選擇的版本目錄 $SELECTED_DIR 不存在。"
  exit 1
fi

# 套用版本
echo "套用 $SELECTED_DIR ..."
cp -r $SELECTED_DIR/. $APP_HOME

echo "套用完成！"
