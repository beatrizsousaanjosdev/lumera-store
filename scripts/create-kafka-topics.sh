#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if command -v podman >/dev/null 2>&1; then
    podman_command=podman
elif [ -x /opt/podman/bin/podman ]; then
    podman_command=/opt/podman/bin/podman
else
    echo 'Podman was not found. Add it to PATH.' >&2
    exit 1
fi
for topic in \
    lumera.product.created.v1 \
    lumera.order.created.v1 \
    lumera.stock.reservation.result.v1 \
    lumera.payment.processed.v1 \
    lumera.payment.requested.v1 \
    lumera.stock.finalization.requested.v1 \
    lumera.stock.finalized.v1 \
    lumera.order.completed.v1; do
    for name in "$topic" "$topic.DLT"; do
        "$podman_command" compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
            --bootstrap-server kafka:29092 --create --if-not-exists \
            --topic "$name" --partitions 3 --replication-factor 1
    done
done
