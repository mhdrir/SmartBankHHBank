output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "db_endpoint" {
  value = aws_db_instance.postgres.address
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}
