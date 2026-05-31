#!/usr/bin/env sh
set -eu

API_BASE="${API_BASE:-http://localhost:8081}"
EMAIL="${EMAIL:-ajay1@gmail.com}"
PASSWORD="${PASSWORD:-Welcome1}"
NODE_ID="${NODE_ID:-56684a65-3dc4-43d3-8c73-e5c42cd86c81}"

login_json="$(mktemp)"
upload_json="$(mktemp)"
preview_json="$(mktemp)"
import_json="$(mktemp)"
csv_file="$(mktemp)"
trap 'rm -f "$login_json" "$upload_json" "$preview_json" "$import_json" "$csv_file"' EXIT

cat > "$csv_file" <<EOF
taxonomyNodeId,actor,questionType,difficulty,workflowStatus,questionText,explanation,sourceReference,licenseCategory,options
$NODE_ID,$EMAIL,MULTIPLE_SELECT,HARD,DRAFT,Which values are even,Pick the even answers,book1,CC-BY,A|2|true;B|4|true;C|3|false;D|5|false
EOF

curl -s -o "$login_json" \
  -X POST "$API_BASE/api/public/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"

token="$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' "$login_json")"
if [ -z "$token" ]; then
  echo "login failed"
  cat "$login_json"
  exit 1
fi

curl -s -o "$upload_json" \
  -F "file=@$csv_file" \
  -H "Authorization: Bearer $token" \
  "$API_BASE/api/admin/media/upload"

object_key="$(sed -n 's/.*"objectKey":"\([^"]*\)".*/\1/p' "$upload_json")"
if [ -z "$object_key" ]; then
  echo "upload failed"
  cat "$upload_json"
  exit 1
fi

curl -s -o "$preview_json" \
  -H "Authorization: Bearer $token" \
  "$API_BASE/api/admin/imports/questions/preview?objectKey=$object_key"

curl -s -o "$import_json" \
  -X POST \
  -H "Authorization: Bearer $token" \
  "$API_BASE/api/admin/imports/questions?objectKey=$object_key"

printf 'UPLOAD\n'
cat "$upload_json"
printf '\nPREVIEW\n'
cat "$preview_json"
printf '\nIMPORT\n'
cat "$import_json"
