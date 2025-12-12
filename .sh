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
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm
rpm -U ./amazon-cloudwatch-agent.rpm

# Create CloudWatch agent config (optional - for system metrics)
cat > /opt/aws/amazon-cloudwatch-agent/bin/config.json << EOF
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/messages",
            "log_group_name": "/ec2/var/log/messages",
            "log_stream_name": "{instance_id}"
          }
        ]
      }
    }
  }
}
EOF

# Start CloudWatch agent
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/bin/config.json -s

# Replace placeholders!
ECR_REGISTRY="786359466101.dkr.ecr.eu-north-1.amazonaws.com"
ECR_REPOSITORY="mandm/parcel-automation-backend"
IMAGE_TAG="latest"
CW_LOG_GROUP="/ec2/docker/shortly-app"
CW_LOG_REGION="eu-north-1"

# Login to ECR
aws ecr get-login-password --region $CW_LOG_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY

# Pull the image
docker pull $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

# Run the container with awslogs driver
docker run -d \
  --restart unless-stopped \
  --log-driver=awslogs \
  --log-opt awslogs-region=$CW_LOG_REGION \
  --log-opt awslogs-group=$CW_LOG_GROUP \
  --log-opt awslogs-create-group=true \
  --log-opt awslogs-stream=shortly-app-$(hostname) \
  -p 80:8080 \
  -e MONGO_URL="mongodb+srv://kingsleybotchwayedu11:UZZIAHPOP%4090@cluster0.qth5ban.mongodb.net/shortly?appName=Cluster0" \
  -e MNOTIFY_API="yRqE0kiFfWB2oCejHyY9mt9Yz" \
  -e JWT_ACCESS_KEY="mySecretAccessKey123456789abcdefghijklmnopqrstuvwxyz" \
  -e JWT_REFRESH_KEY="mySecretRefreshKey987654321zyxwvutsrqponmlkjihgfedcba" \
  -e JWT_EXPIRATION_TIME="286400000" \
  -e FRONTEND_HOST="http://localhost:8080" \
  $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG