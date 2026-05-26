package streambuted.identity.service;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import streambuted.identity.domain.Role;
import streambuted.identity.domain.UserAccountEntity;
import streambuted.identity.grpc.TokenRequest;
import streambuted.identity.grpc.TokenResponse;
import streambuted.identity.grpc.TokenValidatorGrpcService;
import streambuted.identity.repository.UserAccountRepository;
import streambuted.identity.security.JwtProperties;
import streambuted.identity.security.RsaJwtKeyProvider;
import streambuted.identity.security.JwtService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenValidatorGrpcService.
 *
 * Validates the gRPC contract used by internal microservices to verify
 * access tokens without going through the REST API gateway.
 *
 * All collaborators are mocked — no gRPC server is started.
 * The StreamObserver is captured with an ArgumentCaptor to inspect
 * the response written by the service under test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TokenValidatorGrpcService Unit Tests")
class TokenValidatorGrpcServiceTest {

    private static final long ACCESS_EXPIRY_MS = 900_000L;

    @Mock private UserAccountRepository accountRepository;
    @Mock private JwtProperties         jwtProperties;

    private JwtService jwtService;

    @InjectMocks
    private TokenValidatorGrpcService grpcService;

    @Captor
    private ArgumentCaptor<TokenResponse> responseCaptor;

    @SuppressWarnings("unchecked")
    private StreamObserver<TokenResponse> responseObserver;

    private UserAccountEntity activeAccount;
    private UUID              accountId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(jwtProperties.getIssuer()).thenReturn("http://identity-service-test");
        when(jwtProperties.getAudience()).thenReturn("streambuted-api-test");
        when(jwtProperties.getAccessTokenExpiryMs()).thenReturn(ACCESS_EXPIRY_MS);

        jwtService = new JwtService(jwtProperties, new RsaJwtKeyProvider(jwtProperties));

        grpcService = new TokenValidatorGrpcService(jwtService, accountRepository);

        responseObserver = mock(StreamObserver.class);

        accountId = UUID.randomUUID();
        activeAccount = UserAccountEntity.builder()
            .id(accountId)
            .email("artist@example.com")
            .passwordHash("$2a$12$hashed")
            .role(Role.ARTIST)
            .isActive(true)
            .build();
    }

    @Nested
    @DisplayName("Success scenarios")
    class SuccessTests {

        @Test
        @DisplayName("should return is_valid=true with correct identity for a valid token")
        void validate_validToken_returnsCorrectIdentity() {
            String token = jwtService.generateAccessToken(activeAccount);
            TokenRequest request = TokenRequest.newBuilder().setToken(token).build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            verify(responseObserver).onCompleted();
            verify(responseObserver, never()).onError(any());

            TokenResponse response = responseCaptor.getValue();
            assertThat(response.getIsValid()).isTrue();
            assertThat(response.getUserId()).isEqualTo(accountId.toString());
            assertThat(response.getEmail()).isEqualTo("artist@example.com");
            assertThat(response.getRole()).isEqualTo("artist");
            assertThat(response.getIsActive()).isTrue();
            assertThat(response.getErrorMessage()).isEmpty();
        }

        @Test
        @DisplayName("should reflect account active status in the response")
        void validate_inactiveAccount_returnsIsActiveFalse() {
            UserAccountEntity inactiveAccount = UserAccountEntity.builder()
                .id(accountId)
                .email("inactive@example.com")
                .passwordHash("$2a$12$hashed")
                .role(Role.LISTENER)
                .isActive(false)
                .build();

            String token = jwtService.generateAccessToken(inactiveAccount);
            TokenRequest request = TokenRequest.newBuilder().setToken(token).build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(inactiveAccount));

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            TokenResponse response = responseCaptor.getValue();

            assertThat(response.getIsValid()).isTrue();
            assertThat(response.getIsActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Failure scenarios")
    class FailureTests {

        @Test
        @DisplayName("should return is_valid=false for a blank token")
        void validate_blankToken_returnsInvalid() {
            TokenRequest request = TokenRequest.newBuilder().setToken("").build();

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            verify(responseObserver).onCompleted();

            TokenResponse response = responseCaptor.getValue();
            assertThat(response.getIsValid()).isFalse();
            assertThat(response.getErrorMessage()).isNotBlank();
            assertThat(response.getUserId()).isEmpty();
        }

        @Test
        @DisplayName("should return is_valid=false for a malformed JWT string")
        void validate_malformedToken_returnsInvalid() {
            TokenRequest request = TokenRequest.newBuilder().setToken("not.a.valid.jwt").build();

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            TokenResponse response = responseCaptor.getValue();

            assertThat(response.getIsValid()).isFalse();
            assertThat(response.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("should return is_valid=false for a tampered signature")
        void validate_tamperedSignature_returnsInvalid() {
            String validToken = jwtService.generateAccessToken(activeAccount);
            String tampered   = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "badsig";

            TokenRequest request = TokenRequest.newBuilder().setToken(tampered).build();

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            TokenResponse response = responseCaptor.getValue();

            assertThat(response.getIsValid()).isFalse();
        }

        @Test
        @DisplayName("should return is_valid=false for an expired token")
        void validate_expiredToken_returnsInvalid() {
            JwtProperties expiredProps = mock(JwtProperties.class);
            when(expiredProps.getIssuer()).thenReturn("http://identity-service-test");
            when(expiredProps.getAudience()).thenReturn("streambuted-api-test");
            when(expiredProps.getAccessTokenExpiryMs()).thenReturn(-1L); // instantly expired
            JwtService expiredJwtService = new JwtService(expiredProps, new RsaJwtKeyProvider(expiredProps));

            String expiredToken = expiredJwtService.generateAccessToken(activeAccount);
            TokenRequest request = TokenRequest.newBuilder().setToken(expiredToken).build();

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            TokenResponse response = responseCaptor.getValue();

            assertThat(response.getIsValid()).isFalse();
            assertThat(response.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("should return is_valid=false when the token belongs to a deleted user")
        void validate_deletedUser_returnsInvalid() {
            String token = jwtService.generateAccessToken(activeAccount);
            TokenRequest request = TokenRequest.newBuilder().setToken(token).build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onNext(responseCaptor.capture());
            TokenResponse response = responseCaptor.getValue();

            assertThat(response.getIsValid()).isFalse();
            assertThat(response.getErrorMessage()).contains("La cuenta de usuario no existe");
        }

        @Test
        @DisplayName("should always call onCompleted() — never onError() — for graceful handling")
        void validate_alwaysCallsOnCompleted() {
            TokenRequest request = TokenRequest.newBuilder().setToken("garbage").build();

            grpcService.validateToken(request, responseObserver);

            verify(responseObserver).onCompleted();
            verify(responseObserver, never()).onError(any());
        }
    }
}
