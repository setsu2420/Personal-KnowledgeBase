#!/bin/bash
# Kill any process on port 8080
PID=$(lsof -ti:8080 2>/dev/null)
if [ -n "$PID" ]; then
    kill -9 $PID 2>/dev/null
    echo "Killed PID $PID on port 8080"
else
    echo "No process on port 8080"
fi

# Wait a moment then start backend
sleep 1
cd /Users/xiaotianxue/Desktop/個人/软件所/范_副本/backend-springboot
echo "Starting Spring Boot backend..."
./mvnw spring-boot:run -pl .
