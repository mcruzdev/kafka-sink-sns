#!/bin/bash
echo "Creating SNS topic (KafkaSinkSNS)"
awslocal sns create-topic --name KafkaSinkSNS
awslocal sqs create-queue --queue-name KafkaQueue
awslocal sns subscribe --topic-arn "arn:aws:sns:us-east-1:000000000000:KafkaSinkSNS" --protocol sqs --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:KafkaQueue"