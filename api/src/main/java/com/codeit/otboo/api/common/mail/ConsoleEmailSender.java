package com.codeit.otboo.api.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 개발용 메일 발송 구현체.
 * 실제 발송 대신 콘솔에 출력합니다.
 */
@Slf4j
@Component
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void sendTempPassword(String email, String tempPassword) {
        log.info("[임시 비밀번호 발송] to={} password={}", email, tempPassword);
    }
}
