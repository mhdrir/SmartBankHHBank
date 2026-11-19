variable "aws_region" {
  type    = string
  default = "eu-central-1" # Frankfurt - data stays in the EU
}

variable "environment" {
  type    = string
  default = "dev"
}

# The network usually comes from a shared "platform" stack; we only receive its IDs.
variable "vpc_id" {
  type        = string
  description = "VPC the service runs in"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Private subnets for ECS tasks, RDS and MSK (no public IPs)"
}

variable "image_tag" {
  type        = string
  description = "Docker image tag to deploy (the Git commit SHA from CI)"
}

variable "desired_count" {
  type    = number
  default = 2 # two tasks in different availability zones
}
