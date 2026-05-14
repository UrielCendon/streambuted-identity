package streambuted.identity.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
