package streambuted.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerificationCodeGenerator Unit Tests")
class VerificationCodeGeneratorTest {

    @Test
    @DisplayName("should generate a six digit code")
    void generateCode_sixDigits() {
        VerificationCodeGenerator generator = new VerificationCodeGenerator();

        String code = generator.generateCode();

        assertThat(code).matches("\\d{6}");
    }
}
