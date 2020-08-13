package io.apicurio.registry.test;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.demo.ApplicationImpl;
import io.apicurio.registry.demo.domain.LogInput;
import io.apicurio.registry.demo.utils.PropertiesUtil;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.IfExistsType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.strategy.FindLatestIdStrategy;
import io.registry.client.CompatibleClient;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.concurrent.CompletionStage;

import static io.apicurio.registry.demo.utils.PropertiesUtil.property;

/**
 * @author Ales Justin
 */
public class TestMain {
    private static final Logger log = LoggerFactory.getLogger(TestMain.class);

    public static void main(String[] args) throws Exception {
        Properties properties = PropertiesUtil.properties(args);

        // register schema
        String registryUrl_1 = PropertiesUtil.property(properties, "registry.url.1", "http://localhost:8080/api"); // register against 1st node
        try (RegistryService service = CompatibleClient.createCompatible(registryUrl_1)) {
            String artifactId = ApplicationImpl.INPUT_TOPIC + "-value";
            try {
                service.getArtifactMetaData(artifactId); // check if schema already exists
            } catch (WebApplicationException e) {
                CompletionStage<ArtifactMetaData> csa = service.createArtifact(
                    ArtifactType.AVRO,
                    artifactId,
                    IfExistsType.RETURN,
                    new ByteArrayInputStream(LogInput.SCHEMA$.toString().getBytes())
                );
                csa.toCompletableFuture().get();
            }
        }

        String registryUrl_2 = PropertiesUtil.property(properties, "registry.url.2", "http://localhost:8081/api"); // use 2nd node
        properties.put(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
            property(properties, CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
        );
        properties.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, registryUrl_2);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        properties.put(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM, FindLatestIdStrategy.class.getName());

        try (KafkaProducer<String, LogInput> producer = new KafkaProducer<>(properties)) {

            String key1 = "log1";
            LogInput input1 = LogInput.newBuilder()
                                      .setLine("Some log ...")
                                      .setTimestamp(System.currentTimeMillis())
                                      .build();
            producer.send(
                new ProducerRecord<>(ApplicationImpl.INPUT_TOPIC, key1, input1),
                (metadata, e) -> {
                    if (e != null) {
                        log.error("error1: ", e);
                    } else {
                        log.info("input1 = {}", metadata.timestamp());
                    }
                }
            );

            String key2 = "log1";
            LogInput input2 = LogInput.newBuilder()
                                      .setLine("Some log #2 ...")
                                      .setTimestamp(System.currentTimeMillis())
                                      .build();
            producer.send(
                new ProducerRecord<>(ApplicationImpl.INPUT_TOPIC, key2, input2),
                (metadata, e) -> {
                    if (e != null) {
                        log.error("error2: ", e);
                    } else {
                        log.info("input2 = {}", metadata.timestamp());
                    }
                }
            );
        }
    }
}
