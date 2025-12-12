#!/bin/bash

# Update system and install Docker
dnf update -y
dnf install -y docker

# Start and enable Docker service
systemctl start docker
systemctl enable docker

# Add ec2-user to docker group
usermod -aG docker ec2-user

# Optional: Minimal CloudWatch Agent for host metrics only (CPU, mem, disk)
# If you don't need host metrics, you can remove this entire block!
wget https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm
rpm -U ./amazon-cloudwatch-agent.rpm

cat <<'EOF' > /opt/aws/amazon-cloudwatch-agent/bin/config.json
{
  "agent": {
    "metrics_collection_interval": 60,
    "run_as_user": "root"
  },
  "metrics": {
    "append_dimensions": {
      "InstanceId": "${aws:InstanceId}"
    },
    "metrics_collected": {
      "cpu": { "measurement": ["cpu_usage_idle", "cpu_usage_user", "cpu_usage_system"] },
      "disk": { "measurement": ["used_percent"], "resources": ["*"] },
      "mem": { "measurement": ["mem_used_percent"] }
    }
  }
}
EOF

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -c file:/opt/aws/amazon-cloudwatch-agent/bin/config.json -s

# Replace placeholders!
ECR_REGISTRY="786359466101.dkr.ecr.eu-north-1.amazonaws.com/mandm/parcel-automation-backend"
ECR_REPOSITORY="mandm/parcel-automation-backend"
IMAGE_TAG="latest"
CW_LOG_GROUP="/ec2/docker/shortly-app"  # Choose your log group name
CW_LOG_REGION="eu-north-1"             # e.g., us-east-1

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
  --log-opt awslogs-create-group=true \   # Creates the group if it doesn't exist
  --log-opt awslogs-stream=shortly-app-$(hostname) \  # Unique stream per instance/container
  -p 80:8080 \
  -e MONGO_URL="mongodb+srv://kingsleybotchwayedu11:UZZIAHPOP%4090@cluster0.qth5ban.mongodb.net/shortly?appName=Cluster0" \
  -e MNOTIFY_API="yRqE0kiFfWB2oCejHyY9mt9Yz" \
  -e JWT_ACCESS_KEY="mySecretAccessKey123456789abcdefghijklmnopqrstuvwxyz" \
  -e JWT_REFRESH_KEY="mySecretRefreshKey987654321zyxwvutsrqponmlkjihgfedcba" \
  -e JWT_EXPIRATION_TIME="286400000" \
  -e FRONTEND_HOST="http://localhost:8080" \
  $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG