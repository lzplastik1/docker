#!/bin/sh
# Usage: entrypoint.sh proxy|b2bua
set -e
/usr/local/bin/render-config.py
exec flexisip --server "${1:-proxy}" -c /etc/flexisip/flexisip.conf
