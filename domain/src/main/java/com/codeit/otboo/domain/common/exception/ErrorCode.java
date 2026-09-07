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

    // ===== 의상 =====
    // 담당자가 추가

    // ===== 아웃핏 =====
    // 담당자가 추가

    // ===== 피드 =====
    // 담당자가 추가

    // ===== 날씨 =====
    // 담당자가 추가

    // ===== DM =====
    DM_SELF_NOT_ALLOWED(400, "자기 자신과 DM 방을 만들 수 없습니다."),

    // ===== 알림 =====
    // 담당자가 추가
    ;

    private final int status;
    private final String message;
}
