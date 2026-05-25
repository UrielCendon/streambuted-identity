-- Extend usernames to match the application limit of 100 characters.

ALTER TABLE user_profile
  ALTER COLUMN username TYPE varchar(100);

ALTER TABLE registration_verification
  ALTER COLUMN username TYPE varchar(100);
