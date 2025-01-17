package dev.matheuscruz.kafka.consumer;

import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class KafkaIncoming {

    @Inject
    KafkaClientService kafkaClientService;

    @Inject
    SnsAsyncClient snsAsyncClient;

    @ConfigProperty(name = "topic.arn")
    String topicArn;

    @Incoming("greeting")
    public CompletionStage<Void> consumeFromSQS(Message<String> message) {
        System.out.println("Receiving message from SQS: " + message.getPayload());
        return message.ack();
    }

    @Incoming("features")
    @NonBlocking
    public Uni<Void> consume(KafkaRecord<String, byte[]> record) {
        System.out.println("Received record: " + System.currentTimeMillis() + " " + new String(record.getPayload()));
        return Uni.createFrom()
                .completionStage(
                        () -> {
                            return snsAsyncClient.publish(
                                    p -> p.topicArn(topicArn).message(new String(record.getPayload()))
                            ).minimalCompletionStage();
                        }
                )
                .chain(res -> Uni.createFrom().completionStage(record.ack()))
                .onFailure().call(throwable -> {
                    System.out.println("Error while processing Kafka record: " + throwable.getMessage());
                    return Uni.createFrom().voidItem();
                });
    }

    @Outgoing("features-out")
    public Multi<byte[]> generate() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .map(x -> """
                        {
                            "name": "Kafka as a Service"
                        }
                        """.getBytes(StandardCharsets.UTF_8));
    }
}
