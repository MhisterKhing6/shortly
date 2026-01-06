#!/bin/bash

# Update system and install Docker
dnf update -y
dnf install -y docker

# Start and enable Docker service
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group (ignore if already exists)
usermod -aG docker ec2-user || true

# Wait for Docker to be ready
sleep 5

# Install CloudWatch agent

ECR_REGISTRY="786359466101.dkr.ecr.eu-north-1.amazonaws.com/shortly/frontend"
ECR_REPOSITORY="shortly/frontend"
IMAGE_TAG="latest"
CW_LOG_GROUP="/ec2/docker/shortly-app"
CW_LOG_REGION="eu-north-1"

# Login to ECR
aws ecr get-login-password --region $CW_LOG_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY

# Pull the image
docker pull $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

# Run the container with awslogs driver
docker run -d \
  -p 80:8080 \
  -e MONGO_URL="mongodb+srv://kingsleybotchwayedu11:UZZIAHPOP%4090@cluster0.qth5ban.mongodb.net/shortly?appName=Cluster0" \
  -e MNOTIFY_API="yRqE0kiFfWB2oCejHyY9mt9Yz" \
  -e JWT_ACCESS_KEY="mySecretAccessKey123456789abcdefghijklmnopqrstuvwxyz" \
  -e JWT_REFRESH_KEY="mySecretRefreshKey987654321zyxwvutsrqponmlkjihgfedcba" \
  -e JWT_EXPIRATION_TIME="286400000" \
  -e FRONTEND_HOST="http://localhost:8080" \
  -e APP_URL="https://web-server-1368246382.eu-north-1.elb.amazonaws.com" \
  $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG