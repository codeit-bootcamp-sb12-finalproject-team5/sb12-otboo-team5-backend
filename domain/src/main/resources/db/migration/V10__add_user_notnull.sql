UPDATE users
SET email = 'kakao-' || REPLACE(id::text, '-', '') || '@kakao.com'
WHERE email IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_users_email'
    ) THEN
ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);
END IF;
END $$;

ALTER TABLE users
    ALTER COLUMN email SET NOT NULL;