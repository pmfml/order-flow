#!/bin/bash

# ==============================================================================
# Order-Flow Startup Script
# ==============================================================================

# Ensure the script exits on first error
set -e

echo "================================================="
echo "1. Building the project (skipping tests)"
echo "================================================="
./mvnw clean install -DskipTests

echo "================================================="
echo "2. Starting Infrastructure (Docker Compose)"
echo "================================================="
cd infra
docker compose up -d
cd ..

echo "Waiting for infrastructure to be ready (10 seconds)..."
sleep 10

echo "================================================="
echo "3. Starting Microservices in background"
echo "================================================="

# Create a logs directory
mkdir -p logs

echo "Starting API Gateway..."
nohup java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar > logs/api-gateway.log 2>&1 &
API_GW_PID=$!

echo "Starting Inventory Service..."
nohup java -jar inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar > logs/inventory-service.log 2>&1 &
INV_SVC_PID=$!

echo "Starting Order Service..."
nohup java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > logs/order-service.log 2>&1 &
ORD_SVC_PID=$!

echo "Starting Payment Service..."
nohup java -jar payment-service/target/payment-service-0.0.1-SNAPSHOT.jar > logs/payment-service.log 2>&1 &
PAY_SVC_PID=$!

echo "================================================="
echo "All services started!"
echo ""
echo "API Gateway:        http://localhost:8080"
echo "Kafka UI:           http://localhost:8089"
echo "Grafana:            http://localhost:3000 (admin/admin)"
echo ""
echo "Logs are being written to the ./logs/ directory."
echo ""
echo "To tail all logs, run:"
echo "tail -f logs/*.log"
echo ""
echo "To stop the services later, run:"
echo "kill $API_GW_PID $INV_SVC_PID $ORD_SVC_PID $PAY_SVC_PID"
echo "cd infra && docker compose down"
echo "================================================="
