-- Desktop OAuth handoff codes.
-- Apply with the identity migrator role before enabling DESKTOP_AUTH_ENABLED=true.

CREATE TABLE IF NOT EXISTS desktop_auth_code (
  id uuid PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES user_account(id),
  code_hash varchar(512) NOT NULL UNIQUE,
  state_hash varchar(512) NOT NULL,
  redirect_uri varchar(255) NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  used_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_desktop_auth_code_hash ON desktop_auth_code(code_hash);
CREATE INDEX IF NOT EXISTS idx_desktop_auth_code_account ON desktop_auth_code(account_id);
CREATE INDEX IF NOT EXISTS idx_desktop_auth_code_expires_at ON desktop_auth_code(expires_at);
