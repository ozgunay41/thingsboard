package org.thingsboard.server.dao.nosql.mongo.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {
        "org.thingsboard.server.dao.nosql.mongo.repository.transformation" }, mongoTemplateRef = TransformationConfig.MONGO_TEMPLATE)
public class TransformationConfig {
    protected static final String MONGO_TEMPLATE = "transformationMongoTemplate";
}
