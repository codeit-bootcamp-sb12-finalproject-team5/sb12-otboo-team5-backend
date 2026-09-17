-- 기존 비 알림의 제목·본문·읽음 상태·중복 키는 보존한다.
UPDATE notification SET type = 'LEGACY' WHERE type = 'WEATHER_RAIN';

ALTER TABLE notification DROP CONSTRAINT IF EXISTS ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'WEATHER_FORECAST', 'FEED_CREATED', 'ROLE_CHANGED',
    'FEED_LIKED', 'FEED_COMMENTED', 'FOLLOWED', 'DM_RECEIVED', 'LEGACY'
));
