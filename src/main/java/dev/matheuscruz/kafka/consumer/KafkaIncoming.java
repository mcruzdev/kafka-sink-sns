package dev.matheuscruz.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.matheuscruz.avro.Feature;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.aws.sqs.SqsMessage;
import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class KafkaIncoming {

   private static final Logger LOGGER = LoggerFactory.getLogger(KafkaIncoming.class);

   @Inject
   KafkaClientService kafkaClientService;

   @Inject
   SnsAsyncClient snsAsyncClient;

   @Inject
   ObjectMapper mapper;

   @ConfigProperty(name = "topic.arn")
   String topicArn;

   @Incoming("greeting")
   public CompletionStage<Void> consumeFromSQS(SqsMessage<Feature> message) {
      try {
         var messageMap = this.mapper.readValue(message.getMessage().body(), Map.class);
         System.out.println("SQS Message: " + messageMap.get("Message"));
      } catch (JsonProcessingException e) {
         throw new RuntimeException(e);
      }

      return message.ack();
   }

   @Incoming("features")
   @NonBlocking
   @Retry(delay = 10, maxRetries = 2)
   @Fallback(fallbackMethod = "fallback")
   public Uni<Void> consume(Message<ConsumerRecord<String, Feature>> recordMessage) {
      ConsumerRecord<String, Feature> payload = recordMessage.getPayload();
      Feature value = payload.value();
      System.out.println(
            "Received record: " + System.currentTimeMillis() + ", offset: " + payload.offset() + ", partition: "
                  + payload.partition() + ", thread : " + Thread.currentThread().getName());

      return Uni.createFrom().completionStage(() -> {
         try {
            String snsPayload = this.mapper.writeValueAsString(value);
            System.out.println("Sending to SNS: " + snsPayload);
            return snsAsyncClient.publish(p -> p.topicArn(topicArn).message(snsPayload)).minimalCompletionStage();
         } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
         }
      }).chain(res -> Uni.createFrom().completionStage(recordMessage.ack()).onFailure()
            .call(throwable -> Uni.createFrom().failure(throwable))).log();

   }

   @Outgoing("features-out")
   @NonBlocking
   public Multi<Record<String, Feature>> generate() {
      return Multi.createFrom().ticks().every(Duration.ofSeconds(2)).map(x -> {
         Feature feature = new Feature();
         feature.setTitle("Constant title");
         return Record.of(UUID.randomUUID().toString(), feature);
      });
   }

   private void maybeFail() {
      //      new Random().nextBoolean()
      if (false) {
         LOGGER.error("Error on maybeFail method");
         throw new RuntimeException("Resource failure.");
      }
   }

   public Uni<Void> fallback(Message<ConsumerRecord<String, Feature>> recordMessage) {
      System.out.println("Fallback... Ignoring the record... a better fallback method must be implemented!");
      return Uni.createFrom().completionStage(recordMessage.ack());
   }

}
