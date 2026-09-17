-- Recommendation pipeline local test data.
-- Existing data.sql clothes use TEST_USER_ID, so this script deliberately uses the same owner.
-- Run this script before data.sql on a completely empty database, or run it independently on an existing DB.

-- Login account: recommendation-test@otboo.local / password
INSERT INTO public.users (
    id, email, name, password, role, locked, token_version,
    temp_password, temp_password_expires_at, created_at, updated_at, deleted_at
) VALUES (
    '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    'recommendation-test@otboo.local',
    '추천 테스트 사용자',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'USER', false, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.weather_grid (
    id, nx, ny, region_1depth, region_2depth, region_3depth,
    enabled, last_requested_at, created_at, updated_at
) VALUES (
    '0199f000-0000-7000-8000-000000000001',
    999, 999, '테스트', '추천', '더미격자', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- A valid, non-zero 1536-dimensional vector. It is synthetic and exists only to exercise ranking.
INSERT INTO public.profile (
    id, user_id, weather_grid_id, gender, birth_date, location_source,
    temperature_sensitivity, preference_vector, profile_image_url, created_at, updated_at
) VALUES (
    '0199f000-0000-7000-8000-000000000002',
    '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '0199f000-0000-7000-8000-000000000001',
    'OTHER', DATE '1995-01-01', 'MANUAL', 3,
    array_fill(0.01::real, ARRAY[1536])::vector,
    'profiles/recommendation-test/default.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (user_id) DO UPDATE SET
    preference_vector = EXCLUDED.preference_vector,
    weather_grid_id = EXCLUDED.weather_grid_id,
    updated_at = CURRENT_TIMESTAMP;

-- Five BOTTOM candidates (PANTS + SKIRT) and three SHOES candidates.
-- is_owned=true lets the same rows exercise both OOTD and OUTFIT candidate policies.
INSERT INTO public.clothes (
    id, user_id, name, is_owned, preference, image_url, category, gender,
    attribute_text, attribute_vector, created_at, updated_at, deleted_at,
    description, season, brand
) VALUES
(
    '0199f100-0000-7000-8000-000000000001', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '베이지 와이드 코튼 팬츠', true, 5, NULL, 'PANTS', 'BOTH',
    E'카테고리:와이드팬츠\n색상:베이지\n핏:와이드\n소재:면\n패턴:단색/무지\n스타일:미니멀\n설명:여유로운 실루엣의 베이지 코튼 팬츠입니다.',
    array_fill(0.010::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '여유로운 실루엣의 베이지 코튼 팬츠입니다.', 'SPRING', 'TEST BRAND'
),
(
    '0199f100-0000-7000-8000-000000000002', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '블랙 스트레이트 슬랙스', true, 4, NULL, 'PANTS', 'BOTH',
    E'카테고리:슬랙스\n색상:블랙\n핏:스트레이트\n소재:폴리에스터\n패턴:단색/무지\n스타일:포멀\n설명:깔끔한 실루엣의 블랙 슬랙스입니다.',
    array_fill(0.009::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '깔끔한 실루엣의 블랙 슬랙스입니다.', 'FALL', 'TEST BRAND'
),
(
    '0199f100-0000-7000-8000-000000000003', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '인디고 레귤러 데님 팬츠', true, 4, NULL, 'PANTS', 'BOTH',
    E'카테고리:데님팬츠\n색상:블루\n핏:레귤러\n소재:데님\n패턴:단색/무지\n스타일:캐주얼\n설명:데일리로 활용하기 좋은 인디고 데님입니다.',
    array_fill(0.008::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '데일리로 활용하기 좋은 인디고 데님입니다.', 'ALL_SEASON', 'TEST BRAND'
),
(
    '0199f100-0000-7000-8000-000000000004', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '차콜 카고 조거 팬츠', true, 3, NULL, 'PANTS', 'BOTH',
    E'카테고리:조거팬츠\n색상:차콜\n핏:루즈\n소재:나일론\n패턴:단색/무지\n스타일:스포티\n설명:활동성이 좋은 차콜 카고 조거 팬츠입니다.',
    array_fill(0.007::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '활동성이 좋은 차콜 카고 조거 팬츠입니다.', 'SUMMER', 'TEST BRAND'
),
(
    '0199f100-0000-7000-8000-000000000005', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '네이비 플리츠 미디 스커트', true, 4, NULL, 'SKIRT', 'FEMALE',
    E'카테고리:미디스커트\n색상:네이비\n핏:A라인\n소재:폴리에스터\n패턴:단색/무지\n스타일:클래식\n설명:단정한 플리츠 디테일의 네이비 미디 스커트입니다.',
    array_fill(0.006::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '단정한 플리츠 디테일의 네이비 미디 스커트입니다.', 'FALL', 'TEST BRAND'
),
(
    '0199f200-0000-7000-8000-000000000001', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '화이트 레더 스니커즈', true, 5, NULL, 'SHOES', 'BOTH',
    E'카테고리:스니커즈\n색상:화이트\n핏:레귤러\n소재:가죽\n패턴:단색/무지\n스타일:캐주얼\n설명:다양한 착장에 어울리는 화이트 스니커즈입니다.',
    array_fill(0.010::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '다양한 착장에 어울리는 화이트 스니커즈입니다.', 'ALL_SEASON', 'TEST BRAND'
),
(
    '0199f200-0000-7000-8000-000000000002', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '블랙 첼시 부츠', true, 4, NULL, 'SHOES', 'BOTH',
    E'카테고리:부츠\n색상:블랙\n핏:슬림\n소재:가죽\n패턴:단색/무지\n스타일:시크\n설명:간결한 디자인의 블랙 첼시 부츠입니다.',
    array_fill(0.008::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '간결한 디자인의 블랙 첼시 부츠입니다.', 'WINTER', 'TEST BRAND'
),
(
    '0199f200-0000-7000-8000-000000000003', '01a0a35e-1c51-76f2-82a1-6b87eab6ccfa',
    '브라운 스웨이드 로퍼', true, 3, NULL, 'SHOES', 'BOTH',
    E'카테고리:로퍼\n색상:브라운\n핏:레귤러\n소재:스웨이드\n패턴:단색/무지\n스타일:클래식\n설명:차분한 브라운 컬러의 스웨이드 로퍼입니다.',
    array_fill(0.006::real, ARRAY[1536])::vector, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL,
    '차분한 브라운 컬러의 스웨이드 로퍼입니다.', 'FALL', 'TEST BRAND'
)
ON CONFLICT (id) DO UPDATE SET
    preference = EXCLUDED.preference,
    attribute_text = EXCLUDED.attribute_text,
    attribute_vector = EXCLUDED.attribute_vector,
    updated_at = CURRENT_TIMESTAMP;
