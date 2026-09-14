-- 의상테이블 의상설명텍스트, 의상계절감, 의상브랜드 속성추가
-- 의상테이블 속성벡터 차원변경 (768->1536 for text-embedding-3-small)
ALTER TABLE clothes
    ADD COLUMN description      TEXT,
    ADD COLUMN season           VARCHAR(20),
    ADD COLUMN brand            VARCHAR(255);

ALTER TABLE clothes
    ALTER COLUMN attribute_vector TYPE VECTOR(1536);


-- 프로필테이블 온도민감도 속성제약 변경
-- 프로필테이블 속성벡터 차원변경 (768->1536 for text-embedding-3-small)
ALTER TABLE profile
    DROP CONSTRAINT chk_profile_temperature_sensitivity,
    ADD CONSTRAINT chk_profile_temperature_sensitivity
        CHECK (temperature_sensitivity BETWEEN -5 AND 5);

ALTER TABLE profile
    ALTER COLUMN preference_vector TYPE VECTOR(1536);

