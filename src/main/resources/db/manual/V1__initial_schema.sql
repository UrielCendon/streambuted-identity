-- Identity Service initial production schema.
-- Apply with the identity migrator role before starting Identity with DB_DDL_AUTO=validate.

CREATE TABLE IF NOT EXISTS user_account (
  id uuid PRIMARY KEY,
  email varchar(320) NOT NULL UNIQUE,
  password_hash varchar(60) NOT NULL,
  google_subject varchar(255) UNIQUE,
  role varchar(20) NOT NULL,
  is_active boolean NOT NULL,
  password_setup_required boolean NOT NULL,
  banned_at timestamp with time zone,
  banned_until timestamp with time zone,
  ban_reason varchar(500),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS user_profile (
  id uuid PRIMARY KEY,
  account_id uuid NOT NULL UNIQUE REFERENCES user_account(id),
  username varchar(100) NOT NULL UNIQUE,
  bio text,
  profile_image_asset_id varchar(255),
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_token (
  id uuid PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES user_account(id),
  token_value varchar(512) NOT NULL UNIQUE,
  expires_at timestamp with time zone NOT NULL,
  is_revoked boolean NOT NULL,
  created_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_value ON refresh_token(token_value);
CREATE INDEX IF NOT EXISTS idx_refresh_token_account ON refresh_token(account_id);
CREATE INDEX IF NOT EXISTS idx_user_account_banned_until ON user_account(banned_until);

CREATE TABLE IF NOT EXISTS registration_verification (
  id uuid PRIMARY KEY,
  email varchar(320) NOT NULL,
  username varchar(100) NOT NULL,
  password_hash varchar(60) NOT NULL,
  code_hash varchar(60) NOT NULL,
  status varchar(20) NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  verified_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_registration_verification_email_status
  ON registration_verification(email, status);
CREATE INDEX IF NOT EXISTS idx_registration_verification_expires_at
  ON registration_verification(expires_at);

CREATE TABLE IF NOT EXISTS outbox (
  id uuid PRIMARY KEY,
  aggregate_id uuid NOT NULL,
  event_type varchar(100) NOT NULL,
  payload jsonb NOT NULL,
  status varchar(20) NOT NULL,
  retry_count integer NOT NULL,
  created_at timestamp with time zone NOT NULL,
  processed_at timestamp with time zone
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created_at ON outbox(status, created_at);
