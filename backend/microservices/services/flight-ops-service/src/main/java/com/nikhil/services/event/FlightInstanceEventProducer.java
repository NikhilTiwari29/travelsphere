package com.nikhil.services.event;

import com.nikhil.common_lib.event.FlightInstanceCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightInstanceEventProducer {

    /*
     * Kafka topic used for newly created FlightInstance events.
     *
     * Keeping the topic name in one constant avoids repeating hard-coded
     * topic strings throughout the producer.
     */
    private static final String FLIGHT_INSTANCE_CREATED_TOPIC =
            "flight-instance-created";

    private final KafkaTemplate<String, FlightInstanceCreatedEvent> kafkaTemplate;


    public void sendFlightInstanceCreated(
            FlightInstanceCreatedEvent event
    ) {

        log.info(
                "Publishing flight instance created event flightInstanceId={} flightId={} aircraftId={} topic={}",
                event.getFlightInstanceId(),
                event.getFlightId(),
                event.getAircraftId(),
                FLIGHT_INSTANCE_CREATED_TOPIC
        );

        kafkaTemplate
                .send(
                        FLIGHT_INSTANCE_CREATED_TOPIC,
                        String.valueOf(event.getFlightInstanceId()),
                        event
                )
                .whenComplete(
                        (result, exception) -> {

                            if (exception != null) {

                                log.error(
                                        "Failed to publish flight instance created event flightInstanceId={} flightId={} aircraftId={} topic={}",
                                        event.getFlightInstanceId(),
                                        event.getFlightId(),
                                        event.getAircraftId(),
                                        FLIGHT_INSTANCE_CREATED_TOPIC,
                                        exception
                                );

                                return;
                            }

                            log.info(
                                    "Flight instance created event published successfully flightInstanceId={} flightId={} aircraftId={} topic={} partition={} offset={}",
                                    event.getFlightInstanceId(),
                                    event.getFlightId(),
                                    event.getAircraftId(),
                                    FLIGHT_INSTANCE_CREATED_TOPIC,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset()
                            );
                        }
                );
    }
}