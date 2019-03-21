provider "aws" {
  region  = "us-east-1"
  profile = "class-scheduler"
}

terraform {
    backend "s3" {
    bucket = "class-scheduler"
    key    = "terraform.tfstate"
    region = "us-east-1"
  }
}

resource "aws_kms_key" "s3_kms_key" {
  description             = "This key is used to encrypt bucket objects"
  deletion_window_in_days = 10
}

resource "aws_s3_bucket" "class-scheduler" {
  bucket = "class-scheduler"
  acl    = "private"

  versioning {
    enabled = true
  }

  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        kms_master_key_id = "${aws_kms_key.s3_kms_key.arn}"
        sse_algorithm     = "aws:kms"
      }
    }
  }

  lifecycle_rule {
    id      = "RemoveOldResults"
    prefix  = "results/"
    enabled = true

    expiration {
      days = 2
    }

    noncurrent_version_expiration {
      days = 1
    }
  }

  lifecycle_rule {
    id      = "RemoveOldCode"
    prefix  = "code/"
    enabled = true

    noncurrent_version_expiration {
      days = 7
    }
  }
}

resource "aws_s3_bucket" "class-scheduler-web" {
  bucket = "class-scheduler.pojo.us"
  acl    = "public-read"

  website {
    index_document = "index.html"
    error_document = "index.html"
  }
}

data "aws_route53_zone" "primary" {
  name = "pojo.us"
}

resource "aws_route53_record" "class-scheduler" {
  zone_id = "${data.aws_route53_zone.primary.zone_id}"
  name    = "class-scheduler.pojo.us"
  type    = "A"
  alias {
    name = "${aws_s3_bucket.class-scheduler-web.website_domain}"
    zone_id = "${aws_s3_bucket.class-scheduler-web.hosted_zone_id}"
    evaluate_target_health = false
  }
}

resource "aws_s3_bucket_object" "index" {
  bucket = "${aws_s3_bucket.class-scheduler-web.bucket}"
  key    = "index.html"
  source = "../src/main/web/index.html"
  etag   = "${md5(file("../src/main/web/index.html"))}"
  acl    = "public-read"
  content_type = "text/html"
}

resource "aws_s3_bucket_object" "script" {
  bucket = "${aws_s3_bucket.class-scheduler-web.bucket}"
  key    = "script.js"
  source = "../src/main/web/script.js"
  etag   = "${md5(file("../src/main/web/script.js"))}"
  acl    = "public-read"
  content_type = "text/javascript"
}

# can't do it this way because the etag will not match due to the code being encrypted. Will need to upload the file separately
# resource "aws_s3_bucket_object" "code" {
#   bucket = "${aws_s3_bucket.class-scheduler.bucket}"
#   key    = "code/lambda.jar"
#   source = "../target/scheduling-1.0-SNAPSHOT.jar"
#   etag   = "${md5(file("../target/scheduling-1.0-SNAPSHOT.jar"))}"
#   acl    = "private"
#   content_type = "application/java-archive"
# }

resource "aws_lambda_function" "class_scheduler" {
  function_name = "ClassScheduler"

  s3_bucket = "class-scheduler"
  s3_key    = "code/lambda.jar"

  handler = "us.pojo.scheduling.aws.SchedulingLambda::scheduleStudents"
  runtime = "java8"

  # creating the aws s3 client causes metaspace to run out of space. Needs at least 256.
  memory_size = 1024
  timeout = 15

  role = "${aws_iam_role.lambda_exec.arn}"
}

resource "aws_iam_role" "lambda_exec" {
  name = "ClassSchedulerLambdaRole"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com",
        "AWS": "arn:aws:iam::022291592860:user/admin"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_cloudwatch_log_group" "class_scheduler" {
  name              = "/aws/lambda/${aws_lambda_function.class_scheduler.function_name}"
  retention_in_days = 1
}

# See also the following AWS managed policy: AWSLambdaBasicExecutionRole
resource "aws_iam_policy" "lambda_logging" {
  name = "lambda_logging"
  path = "/"
  description = "IAM policy for logging from a lambda"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*",
      "Effect": "Allow"
    }
  ]
}
EOF
}

# See also the following AWS managed policy: AWSLambdaBasicExecutionRole
resource "aws_iam_policy" "lamba_s3" {
  name = "lambda_s3"
  path = "/"
  description = "IAM policy for writing and reading in s3 bucket"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:GetEncryptionConfiguration"
      ],
      "Resource": ["${aws_s3_bucket.class-scheduler.arn}","${aws_s3_bucket.class-scheduler.arn}/results/*"],
      "Effect": "Allow"
    },
    {
      "Action": [
        "kms:Decrypt",
        "kms:Encrypt",
        "kms:GenerateDataKey",
        "kms:ReEncryptTo",
        "kms:GenerateDataKeyWithoutPlaintext",
        "kms:DescribeKey",
        "kms:ReEncryptFrom"
      ],
      "Resource": "${aws_kms_key.s3_kms_key.arn}",
      "Effect": "Allow"      
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role = "${aws_iam_role.lambda_exec.name}"
  policy_arn = "${aws_iam_policy.lambda_logging.arn}"
}

resource "aws_iam_role_policy_attachment" "lambda_s3" {
  role = "${aws_iam_role.lambda_exec.name}"
  policy_arn = "${aws_iam_policy.lamba_s3.arn}"
}

resource "aws_api_gateway_rest_api" "class_scheduler" {
  name        = "class-scheduler"
  description = "API for class-scheduler.pojo.us"

  minimum_compression_size = 10240
  body = <<OPENAPI
openapi: 3.0.0
servers:
  - description: AWS US-East-1
    url: https://ltv50g5cj3.execute-api.us-east-1.amazonaws.com/
info:
  description: Provided with several CSVs, will schedule students into different classes.
  version: "1.0.0"
  title: Class-Scheduler API
  contact:
    email: ben.lamonica@gmail.com
tags:
paths:
  /schedule:
    post:
      summary: generates a schedule for the provided classes and students
      operationId: generateSchedule
      description: Adds an item to the system
      responses:
        '200':
          description: schedule generated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SchedulingResponse'
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SchedulingRequest'
        description: Request
components:
  schemas:
    SchedulingRequest:
      type: object
      required:
        - classSchedule
        - students
      properties:
        classSchedule:
          type: string
          format: base64 encoded csv
          example:
        students:
          type: string
          format: base64 encoded csv
          example:
        existingSchedule:
          type: string
          format: base64 encoded csv
    SchedulingResponse:
      type: object
      required:
        - message
      properties:
        message:
          type: string
          example: Success.
        assignmentUrl:
          type: string
          format: url
          example: 'https://www.acme-corp.com'
        classSizesUrl:
          type: string
          format: url
          example: 'https://www.acme-corp.com'
        studentsMissingAssignments:
          type: integer
          format: numeric
          example: 50
OPENAPI
}

data "aws_api_gateway_resource" "scheduling" {
  rest_api_id = "${aws_api_gateway_rest_api.class_scheduler.id}"
  path = "/schedule"
}

resource "aws_api_gateway_integration" "lambda" {
  rest_api_id = "${aws_api_gateway_rest_api.class_scheduler.id}"
  resource_id = "${data.aws_api_gateway_resource.scheduling.id}"
  http_method = "POST"

  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "${aws_lambda_function.class_scheduler.invoke_arn}"
}

resource "aws_api_gateway_deployment" "api_gateway" {
  depends_on = [
    "aws_api_gateway_integration.lambda"
  ]

  rest_api_id = "${aws_api_gateway_rest_api.class_scheduler.id}"
  stage_name  = "prod"
}

resource "aws_api_gateway_stage" "prod" {
  stage_name    = "prod"
  rest_api_id   = "${aws_api_gateway_rest_api.class_scheduler.id}"
  deployment_id = "${aws_api_gateway_deployment.api_gateway.id}"

  access_log_settings {
    destination_arn = "${aws_cloudwatch_log_group.class_scheduler.arn}"
    format = "$context.identity.sourceIp $context.identity.caller $context.identity.user [$context.requestTime] \"$context.httpMethod $context.resourcePath $context.protocol\" $context.status $context.responseLength $context.requestId"
  }
}

resource "aws_api_gateway_method_settings" "settings" {
  rest_api_id = "${aws_api_gateway_rest_api.class_scheduler.id}"
  stage_name  = "${aws_api_gateway_stage.prod.stage_name}"
  method_path = "*/*"

  settings {
    metrics_enabled = false
    logging_level   = "INFO"
  }
}
resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = "${aws_lambda_function.class_scheduler.arn}"
  principal     = "apigateway.amazonaws.com"

  # The /*/* portion grants access from any method on any resource
  # within the API Gateway "REST API".
  source_arn = "${aws_api_gateway_deployment.api_gateway.execution_arn}/*/*"
}


resource "aws_api_gateway_account" "account" {
  cloudwatch_role_arn = "${aws_iam_role.cloudwatch.arn}"
}

resource "aws_iam_role" "cloudwatch" {
  name = "api_gateway_cloudwatch_global"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": "apigateway.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
}

resource "aws_iam_role_policy" "cloudwatch" {
  name = "default"
  role = "${aws_iam_role.cloudwatch.id}"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:DescribeLogGroups",
                "logs:DescribeLogStreams",
                "logs:PutLogEvents",
                "logs:GetLogEvents",
                "logs:FilterLogEvents"
            ],
            "Resource": "*"
        }
    ]
}
EOF
}

output "base_url" {
  value = "${aws_api_gateway_deployment.api_gateway.invoke_url}"
}
