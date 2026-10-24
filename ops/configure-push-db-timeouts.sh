#!/usr/bin/env bash
# Run as root on the existing Ubuntu/MariaDB host. Does not restart services.
set -euo pipefail

if [[ $(id -u) != 0 ]]; then
  echo "Bu skripti VPS-də root ilə işlədin." >&2
  exit 1
fi
command -v mariadb >/dev/null
command -v mariadbd >/dev/null
config=/etc/mysql/mariadb.conf.d/99-zakaz-push-timeouts.cnf
if ! grep -Eq '^[[:space:]]*!includedir[[:space:]]+/etc/mysql/mariadb.conf.d/?([[:space:]]|$)' /etc/mysql/my.cnf; then
  echo "MariaDB include yolu fərqlidir; fayl dəyişdirilmədi." >&2
  exit 1
fi
if [[ -f "$config" ]]; then
  cp -a -- "$config" "$config.backup-$(date +%Y%m%d-%H%M%S)"
fi
umask 022
cat > "$config" <<'CONFIG'
# Hikari maxLifetime=300s, keepalive=60s; server idle timeout must be longer.
[mysqld]
wait_timeout=600
interactive_timeout=600
CONFIG
chmod 644 "$config"
# Show only these non-secret options, never the rest of the server defaults.
mariadbd --print-defaults 2>/dev/null | tr ' ' '\n' | grep -E '^--(wait[-_]timeout|interactive[-_]timeout)='
mariadb -e "SET GLOBAL wait_timeout=600; SET GLOBAL interactive_timeout=600; SHOW GLOBAL VARIABLES WHERE Variable_name IN ('wait_timeout','interactive_timeout');"
echo "Ayar saxlandı. Baza restart edilmədi. Mövcud bağlantılar əvvəlki sessiya limitini saxlayır; yeniləri 600 saniyə ilə açılır."
