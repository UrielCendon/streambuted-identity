package streambuted.identity.service.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import streambuted.identity.config.GoogleOAuthProperties;
import streambuted.identity.exception.GoogleAuthenticationException;

import java.net.URI;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthHttpClient implements GoogleOAuthClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public GoogleUserInfo exchangeCode(String code, GoogleOAuthProperties properties) {
        try {
            GoogleTokenResponse token = requestToken(code, properties);
            GoogleTokenInfoResponse tokenInfo = requestTokenInfo(token.idToken(), properties);
            validateTokenInfo(tokenInfo, properties);

            return new GoogleUserInfo(tokenInfo.subject(), tokenInfo.email(), tokenInfo.name());
        } catch (RestClientException ex) {
            log.warn("Google OAuth exchange failed while calling Google API: {}", ex.getClass().getSimpleName());
            throw new GoogleAuthenticationException("Google OAuth exchange failed.");
        }
    }

    private GoogleTokenResponse requestToken(String code, GoogleOAuthProperties properties) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getCallbackUrl());
        form.add("grant_type", "authorization_code");

        ResponseEntity<GoogleTokenResponse> response = restTemplate.postForEntity(
            properties.getTokenUrl(),
            new HttpEntity<>(form, headers),
            GoogleTokenResponse.class
        );

        GoogleTokenResponse body = response.getBody();
        if (body == null || body.idToken() == null || body.idToken().isBlank()) {
            throw new GoogleAuthenticationException("Google did not return an identity token.");
        }

        return body;
    }

    private GoogleTokenInfoResponse requestTokenInfo(String idToken, GoogleOAuthProperties properties) {
        URI uri = UriComponentsBuilder
            .fromHttpUrl(properties.getTokenInfoUrl())
            .queryParam("id_token", idToken)
            .build(true)
            .toUri();

        GoogleTokenInfoResponse tokenInfo = restTemplate.getForObject(uri, GoogleTokenInfoResponse.class);
        if (tokenInfo == null) {
            throw new GoogleAuthenticationException("Google token verification failed.");
        }

        return tokenInfo;
    }

    private void validateTokenInfo(
        GoogleTokenInfoResponse tokenInfo,
        GoogleOAuthProperties properties
    ) {
        if (!properties.getClientId().equals(tokenInfo.audience())) {
            throw new GoogleAuthenticationException("Google token audience is invalid.");
        }

        if (!Boolean.TRUE.equals(tokenInfo.emailVerified())) {
            throw new GoogleAuthenticationException("Google email is not verified.");
        }

        if (tokenInfo.subject() == null || tokenInfo.subject().isBlank()) {
            throw new GoogleAuthenticationException("Google account identifier is missing.");
        }

        if (tokenInfo.email() == null || tokenInfo.email().isBlank()) {
            throw new GoogleAuthenticationException("Google email is missing.");
        }
    }

    private record GoogleTokenResponse(
        @JsonProperty("id_token") String idToken
    ) {}

    private record GoogleTokenInfoResponse(
        @JsonProperty("aud") String audience,
        @JsonProperty("sub") String subject,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name
    ) {}
}
