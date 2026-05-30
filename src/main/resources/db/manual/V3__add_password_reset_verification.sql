CREATE TABLE IF NOT EXISTS password_reset_verification (
  id uuid PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES user_account(id),
  email varchar(320) NOT NULL,
  code_hash varchar(60) NOT NULL,
  status varchar(20) NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  verified_at timestamp with time zone,
  completed_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL,
  updated_at timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_password_reset_verification_email_status
  ON password_reset_verification(email, status);
CREATE INDEX IF NOT EXISTS idx_password_reset_verification_expires_at
  ON password_reset_verification(expires_at);
