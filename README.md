
# Spring Boot Integration with Confluent Kafka 

## Payment Flow
### Payment Ingestor Service

- REST API /api/payments - POST
   - This API is going to do validate the details and produce the message to Kafka topic , Refer kafka package for all the Producer implementation. Kafka configuration is setup is application.properties.

## Confluent Kafka Setup

- Sign up and Setup Confluent Account
- Sign into the Confluent Cloud Console
- Creating a new cluster
- After cluster creation - obtain Kafka cluster bootstrap server configuration
- Select Basic Authentication Mechanism and generate the API configuration
- Copy CLUSTER API KEY and CLUSTER API SECRET
- Update Kafka bootstrap server details, APIKEY and SECRET in project application.properties file.

## Reference

### Payment-processor - Consumer application for reading the messages from this payment-ingestor

- Repository Link :  https://github.com/pavan-bommideni/payment-processor












## Tech Stack

**Stack:** Java 21, Spring boot, H2 , Confluent Kafka


## Documentation

[Confluent Kafka Documentation](https://developer.confluent.io/get-started/spring-boot/#introduction)

