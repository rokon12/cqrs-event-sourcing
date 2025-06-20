package ca.bazlur.eventsourcing.config;

import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Map;

@ApplicationScoped
public class ValidationConfig {
    
    @ApplicationScoped
    public Validator validator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }
}