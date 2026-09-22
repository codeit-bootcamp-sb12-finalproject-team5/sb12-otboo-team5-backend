package com.codeit.otboo.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ===== 공통 =====
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(400, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(404, "요청하신 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청 방식입니다."),

    // ===== 사용자 / 인증 =====
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(403, "잠긴 계정입니다."),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "만료된 토큰입니다."),
    TOKEN_VERSION_MISMATCH(401, "로그아웃된 세션입니다. 다시 로그인해 주세요."),
    TEMP_PASSWORD_EXPIRED(401, "임시 비밀번호가 만료되었습니다."),
    ACCESS_DENIED(403, "접근 권한이 없습니다."),

    // ===== 프로필 =====
    PROFILE_NOT_FOUND(404, "프로필을 찾을 수 없습니다."),
    PROFILE_PREFERENCE_NOT_READY(422, "사용자 선호 정보가 부족하여 Outfit 추천을 수행할 수 없습니다."),

    // =====팔로우 =====
    FOLLOW_NOT_FOUND(404, "팔로우를 찾을 수 없습니다."),
    DUPLICATE_FOLLOW(409, "이미 팔로우한 사용자입니다."),
    SELF_FOLLOW_NOT_ALLOWED(400, "자기 자신을 팔로우할 수 없습니다."),

    // ===== 의상 =====
    CLOTHES_NOT_FOUND(404, "의상을 찾을 수 없습니다"),
    CLOTHES_ANALYSIS_FAILED(502, "의상 분석에 실패했습니다."),
    INVALID_ATTRIBUTE_VALUE(400, "유효하지 않은 의상 속성입니다."),
    CLOTHES_ATTRIBUTE_PARSE_FAILED(500, "의상 속성작업에 실패했습니다."),

    // ===== 아웃핏 =====
    OUTFIT_NOT_FOUND(404, "Outfit을 찾을 수 없습니다."),
    DUPLICATE_OUTFIT_CLOTHES(409, "Outfit에 동일한 의상이 중복되어 있습니다."),
    RECOMMENDATION_GENERATION_FAILED(502, "추천 생성에 실패했습니다."),

    // ===== 피드 =====
    // 담당자가 추가

    // ===== 날씨 =====
    WEATHER_DATA_UNAVAILABLE(503, "날씨 데이터를 가져올 수 없습니다."),
    INVALID_LOCATION_INPUT(400, "위치 입력값이 올바르지 않습니다."),

    // ===== 배치 =====
    INVALID_BATCH_CLEANUP_DATE(400, "cleanupDate는 yyyy-MM-dd 형식의 날짜여야 합니다."),
    BATCH_WEATHER_DATA_UNAVAILABLE(503, "격자의 관측·예보 데이터를 하나도 수집하지 못했습니다."),
    INVALID_BATCH_COLLECTION_TIME(400, "collectionAt은 offset을 포함한 ISO 시각이어야 합니다."),
    BATCH_TRANSACTION_REQUIRED(500, "배치 저장에는 활성 트랜잭션이 필요합니다."),

    // ===== DM =====
    DM_SELF_NOT_ALLOWED(400, "자기 자신과 DM 방을 만들 수 없습니다."),
    ROOM_NOT_FOUND(404, "DM 방을 찾을 수 없습니다."),
    DM_MESSAGE_NOT_FOUND(404, "DM 메시지를 찾을 수 없습니다."),
    INVALID_MESSAGE(400, "메시지 내용이 올바르지 않습니다."),
    FORBIDDEN(403, "해당 DM 방에서 메시지를 전송할 권한이 없습니다."),

    // ===== 알림 =====
    NOTIFICATION_NOT_FOUND(400, "알림을 찾을 수 없습니다."),
    NOTIFICATION_STREAM_UNAVAILABLE(503, "알림 연결을 준비 중입니다. 잠시 후 다시 시도해 주세요."),
    INVALID_NOTIFICATION_BROADCAST(400, "알림 브로드캐스트 메시지가 올바르지 않습니다."),
    UNSUPPORTED_NOTIFICATION_TYPE(400, "지원하지 않는 알림 유형입니다."),
    DUPLICATE_NOTIFICATION_HANDLER(500, "알림 유형의 처리기가 중복 등록되었습니다."),
    NOTIFICATION_SOURCE_NOT_FOUND(404, "알림 원본이 없거나 수신자가 접근할 수 없습니다."),
    NOTIFICATION_PUBLICATION_FAILED(503, "알림 생성 이벤트 발행에 실패했습니다."),
    NOTIFICATION_CONTINUATION_FAILED(500, "다음 알림 페이지 작업을 전달하지 못했습니다."),
    NOTIFICATION_PROCESSING_INTERRUPTED(500, "알림 처리가 중단되었습니다.")
    ;

    private final int status;
    private final String message;
}
