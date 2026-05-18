package streambuted.identity.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.RsaJwtKeyProvider;
import streambuted.identity.security.JwtService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtService (the TokenProvider equivalent).
 *
 * Coverage targets:
 *  - Token generation: valid structure, embedded claims
 *  - Token validation: success, expired, tampered signature, blank input
 *  - Claims extraction: userId, email, role
 *  - Expiry helper method
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private static final long ACCESS_EXPIRY_MS  = 900_000L;  // 15 min
    private static final long REFRESH_EXPIRY_MS = 604_800_000L; // 7 days

    @Mock
    private JwtProperties jwtProperties;

    private JwtService jwtService;

    private UserAccountEntity testAccount;

    @BeforeEach
    void setUp() {
        when(jwtProperties.getIssuer()).thenReturn("http://identity-service-test");
        when(jwtProperties.getAudience()).thenReturn("streambuted-api-test");
        when(jwtProperties.getAccessTokenExpiryMs()).thenReturn(ACCESS_EXPIRY_MS);

        RsaJwtKeyProvider rsaJwtKeyProvider = new RsaJwtKeyProvider(jwtProperties);
        jwtService = new JwtService(jwtProperties, rsaJwtKeyProvider);

        testAccount = UserAccountEntity.builder()
            .id(UUID.randomUUID())
            .email("user@example.com")
            .passwordHash("$2a$12$hashed")
            .role(Role.ARTIST)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateTokenTests {

        @Test
        @DisplayName("should generate a non-blank, three-part JWT")
        void generateToken_returnsValidJwtFormat() {
            String token = jwtService.generateAccessToken(testAccount);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("should embed userId as subject claim")
        void generateToken_embedsUserIdAsSubject() {
            String token = jwtService.generateAccessToken(testAccount);

            UUID extractedId = jwtService.extractUserId(token);
            assertThat(extractedId).isEqualTo(testAccount.getId());
        }

        @Test
        @DisplayName("should embed email and role claims")
        void generateToken_embedsEmailAndRoleClaims() {
            String token = jwtService.generateAccessToken(testAccount);

            var claims = jwtService.extractClaims(token);
            assertThat(claims).isPresent();
            assertThat(claims.get().get("email", String.class)).isEqualTo("user@example.com");
            assertThat(claims.get().get("role",  String.class)).isEqualTo("artist");
            assertThat(claims.get().getAudience()).contains("streambuted-api-test");
        }

        @Test
        @DisplayName("should generate different tokens for the same user on consecutive calls")
        void generateToken_producesDifferentTokensPerCall() throws InterruptedException {
            String first  = jwtService.generateAccessToken(testAccount);
            Thread.sleep(10); // ensure different iat timestamp
            String second = jwtService.generateAccessToken(testAccount);

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValidTests {

        @Test
        @DisplayName("should return true for a freshly issued token")
        void isTokenValid_freshToken_returnsTrue() {
            String token = jwtService.generateAccessToken(testAccount);
            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("should return false for a token with tampered signature")
        void isTokenValid_tamperedSignature_returnsFalse() {
            String token   = jwtService.generateAccessToken(testAccount);
            String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsig";

            assertThat(jwtService.isTokenValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("should return false for a completely malformed string")
        void isTokenValid_malformedString_returnsFalse() {
            assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("should return false for a blank token")
        void isTokenValid_blankToken_returnsFalse() {
            assertThat(jwtService.isTokenValid("")).isFalse();
            assertThat(jwtService.isTokenValid("   ")).isFalse();
        }

        @Test
        @DisplayName("should return false for an expired token")
        void isTokenValid_expiredToken_returnsFalse() {
            when(jwtProperties.getAccessTokenExpiryMs()).thenReturn(-1L);

            String expiredToken = jwtService.generateAccessToken(testAccount);

            assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
        }

        @Test
        @DisplayName("should return false for a token signed with a different key")
        void isTokenValid_differentKey_returnsFalse() {
            String token = jwtService.generateAccessToken(testAccount);

            JwtProperties otherProps = mock(JwtProperties.class);
            when(otherProps.getIssuer()).thenReturn("http://identity-service-test");
            when(otherProps.getAudience()).thenReturn("streambuted-api-test");
            when(otherProps.getAccessTokenExpiryMs()).thenReturn(ACCESS_EXPIRY_MS);

            RsaJwtKeyProvider otherKeyProvider = new RsaJwtKeyProvider(otherProps);
            JwtService otherService = new JwtService(otherProps, otherKeyProvider);

            assertThat(otherService.isTokenValid(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("extractClaims() / extractUserId()")
    class ExtractClaimsTests {

        @Test
        @DisplayName("should return empty Optional for an invalid token")
        void extractClaims_invalidToken_returnsEmpty() {
            Optional<?> result = jwtService.extractClaims("garbage.token.string");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return non-empty Optional with correct subject for a valid token")
        void extractClaims_validToken_returnsCorrectSubject() {
            String token  = jwtService.generateAccessToken(testAccount);
            var    claims = jwtService.extractClaims(token);

            assertThat(claims).isPresent();
            assertThat(claims.get().getSubject()).isEqualTo(testAccount.getId().toString());
        }

        @Test
        @DisplayName("extractUserId() should throw for an invalid token")
        void extractUserId_invalidToken_throwsException() {
            assertThatThrownBy(() -> jwtService.extractUserId("invalid.token"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("extractUserId() should return correct UUID for valid token")
        void extractUserId_validToken_returnsCorrectUuid() {
            String token = jwtService.generateAccessToken(testAccount);
            UUID   id    = jwtService.extractUserId(token);

            assertThat(id).isEqualTo(testAccount.getId());
        }
    }

    @Test
    @DisplayName("getAccessTokenExpirySeconds() should convert ms to seconds correctly")
    void getAccessTokenExpirySeconds_convertsCorrectly() {
        when(jwtProperties.getAccessTokenExpiryMs()).thenReturn(900_000L);
        assertThat(jwtService.getAccessTokenExpirySeconds()).isEqualTo(900L);
    }
}
