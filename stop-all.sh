#!/bin/bash

# ==============================================================================
# Order-Flow Shutdown Script
# ==============================================================================

echo "================================================="
echo "1. Stopping Microservices"
echo "================================================="

# Find and kill the Java processes for our services
for service in "api-gateway" "inventory-service" "order-service" "payment-service"; do
    PID=$(ps aux | grep "[j]ava -jar ${service}/target/${service}-0.0.1-SNAPSHOT.jar" | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo "Stopping $service (PID: $PID)..."
        kill $PID
    else
        echo "$service is not running."
    fi
done

echo "================================================="
echo "2. Stopping Infrastructure (Docker Compose)"
echo "================================================="
cd infra
docker compose down
cd ..

echo "================================================="
echo "All services stopped successfully!"
echo "================================================="
