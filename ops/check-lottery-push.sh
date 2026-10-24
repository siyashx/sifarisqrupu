#!/usr/bin/env bash
# Read-only diagnostics: never prints tokens, passwords, names, or message bodies.
set -euo pipefail
lottery_id=${1:-}
user_id=${2:-}
database=${PUSH_DATABASE:-sifarisqrupu}
if [[ ! "$lottery_id" =~ ^[1-9][0-9]{0,17}$ ]] || [[ -n "$user_id" && ! "$user_id" =~ ^[1-9][0-9]{0,17}$ ]] || [[ ! "$database" =~ ^[a-zA-Z0-9_]+$ ]]; then
  echo "İstifadə: bash ops/check-lottery-push.sh LOTOREYA_ID [USER_ID]" >&2
  exit 1
fi
user_filter=''
if [[ -n "$user_id" ]]; then user_filter=" AND d.user_id=$user_id"; fi
mariadb --table --database="$database" <<SQL
SELECT NOW() AS server_time, @@GLOBAL.wait_timeout AS global_idle_seconds, @@SESSION.wait_timeout AS this_session_idle_seconds;
SELECT d.event_id, d.app_code, COALESCE(p.platform,'missing') AS platform,
       d.state, COALESCE(d.last_error,'') AS reason, COUNT(*) AS devices
FROM push_delivery d LEFT JOIN push_device p ON p.id=d.device_id
WHERE d.event_id LIKE 'lottery-$lottery_id-%'$user_filter
GROUP BY d.event_id,d.app_code,p.platform,d.state,d.last_error
ORDER BY d.event_id,d.app_code,d.state;
SELECT d.id AS delivery_id,d.event_id,d.app_code,d.device_id,d.state,d.attempts,
       COALESCE(d.last_error,'') AS reason,
       COALESCE(p.enabled,0) AS device_enabled,
       GREATEST(0,ROUND((d.expires_at-UNIX_TIMESTAMP()*1000)/1000)) AS ttl_seconds
FROM push_delivery d LEFT JOIN push_device p ON p.id=d.device_id
WHERE d.event_id LIKE 'lottery-$lottery_id-%'$user_filter
ORDER BY d.id DESC LIMIT 30;
SQL
if [[ -n "$user_id" ]]; then
  mariadb --table --database="$database" -e "SELECT id AS device_id,app_code,platform,active_role,enabled,FROM_UNIXTIME(updated_at/1000) AS last_sync FROM push_device WHERE user_id=$user_id ORDER BY updated_at DESC;"
fi
