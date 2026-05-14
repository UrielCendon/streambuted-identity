package streambuted.identity.service.oauth;

import streambuted.identity.config.GoogleOAuthProperties;

public interface GoogleOAuthClient {

    GoogleUserInfo exchangeCode(String code, GoogleOAuthProperties properties);
}
