# Terraform = Infrastructure as Code: the AWS setup is described in files, reviewed in pull requests,
# and applied with "terraform plan" (show the diff) and "terraform apply" (make it so).
#
# The state file (what Terraform has created) lives in S3 with locking, so a team can share it and
# two people cannot apply at the same time.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state. Bucket must exist before "terraform init" (created once, by hand or a bootstrap stack).
  backend "s3" {
    bucket       = "smartbank-terraform-state"
    key          = "payments/terraform.tfstate"
    region       = "eu-central-1"
    encrypt      = true
    use_lockfile = true # S3-native state locking (Terraform >= 1.10); older setups use a DynamoDB table
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      project     = "smartbank-payments"
      environment = var.environment
      managed-by  = "terraform"
    }
  }
}
