# AWS target architecture for smartbank-payments (sketch for learning, not applied anywhere):
#
#   ECR (images) -> ECS Fargate service (2 tasks, private subnets)
#                     |-- RDS PostgreSQL       (payments, outbox, Camunda tables)
#                     |-- MSK Serverless       (Kafka topics)
#                     |-- Secrets Manager      (DB password, JWT secret)
#                     '-- CloudWatch Logs      (application logs)
#
# Not included to keep it short: load balancer (ALB) + WAF in front of the API, autoscaling,
# alarms, backups policy, KMS customer-managed keys.

locals {
  name = "smartbank-payments-${var.environment}"
}

# ---------------- container registry ----------------
resource "aws_ecr_repository" "app" {
  name                 = "smartbank-payments"
  image_tag_mutability = "IMMUTABLE" # a tag (commit SHA) always means the same image -> traceable releases

  image_scanning_configuration {
    scan_on_push = true # vulnerability scan of every pushed image
  }
}

# ---------------- secrets ----------------
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "${local.name}/db-password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

# ---------------- security groups (who may talk to whom) ----------------
resource "aws_security_group" "app" {
  name   = "${local.name}-app"
  vpc_id = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name   = "${local.name}-db"
  vpc_id = var.vpc_id

  ingress {
    description     = "PostgreSQL only from the app"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

resource "aws_security_group" "kafka" {
  name   = "${local.name}-kafka"
  vpc_id = var.vpc_id

  ingress {
    description     = "Kafka (IAM auth port) only from the app"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

# ---------------- database ----------------
resource "aws_db_subnet_group" "db" {
  name       = local.name
  subnet_ids = var.private_subnet_ids
}

resource "aws_db_instance" "postgres" {
  identifier              = local.name
  engine                  = "postgres"
  engine_version          = "16"
  instance_class          = "db.t4g.micro"
  allocated_storage       = 20
  db_name                 = "smartbank"
  username                = "smartbank"
  password                = random_password.db.result
  db_subnet_group_name    = aws_db_subnet_group.db.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  storage_encrypted       = true # encryption at rest
  backup_retention_period = 7
  multi_az                = var.environment == "prod" # standby in a second AZ for production
  skip_final_snapshot     = var.environment != "prod"
  deletion_protection     = var.environment == "prod"
}

# ---------------- Kafka ----------------
# MSK Serverless only supports IAM authentication: the app would add the "aws-msk-iam-auth" library
# and set security.protocol=SASL_SSL, sasl.mechanism=AWS_MSK_IAM.
resource "aws_msk_serverless_cluster" "kafka" {
  cluster_name = local.name

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [aws_security_group.kafka.id]
  }

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }
}

# ---------------- logging ----------------
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = 30
}

# ---------------- IAM ----------------
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: used by ECS itself to pull the image, write logs and read secrets at startup
resource "aws_iam_role" "execution" {
  name               = "${local.name}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_password.arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.read_secrets.json
}

# Task role: permissions of the application code itself (least privilege: only our Kafka cluster)
resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

data "aws_iam_policy_document" "kafka_access" {
  statement {
    actions   = ["kafka-cluster:*"]
    resources = ["${aws_msk_serverless_cluster.kafka.arn}", "${aws_msk_serverless_cluster.kafka.arn}/*"]
  }
}

resource "aws_iam_role_policy" "task_kafka" {
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.kafka_access.json
}

# ---------------- ECS Fargate ----------------
resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name         = "app"
    image        = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = 8080 }]
    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/smartbank" },
      { name = "DB_USER", value = "smartbank" },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_serverless_cluster.kafka.bootstrap_brokers_sasl_iam }
    ]
    # Injected as env var at container start; never stored in the task definition or in Git
    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn }
    ]
    healthCheck = {
      command  = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health/liveness || exit 1"]
      interval = 30
      retries  = 3
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "app"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  name            = local.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  # Rolling deployment: new tasks must be healthy before old ones stop; automatic rollback on failure
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
}
