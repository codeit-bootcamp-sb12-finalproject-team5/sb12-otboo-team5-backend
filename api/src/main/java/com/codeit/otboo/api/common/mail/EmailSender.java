package com.codeit.otboo.api.common.mail;

/**
 * 메일 발송 추상화.
 * 현재는 콘솔 출력 구현체를 사용하며, SMTP 도입 시 구현체만 교체합니다.
 */
public interface EmailSender {

    void sendTempPassword(String email, String tempPassword);
}
