package dev.matheuscruz.kafka.consumer;

import dev.matheuscruz.avro.Feature;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class FeatureDeserializer extends ObjectMapperDeserializer<Feature> {
   public FeatureDeserializer() {
      super(Feature.class);
   }
}
