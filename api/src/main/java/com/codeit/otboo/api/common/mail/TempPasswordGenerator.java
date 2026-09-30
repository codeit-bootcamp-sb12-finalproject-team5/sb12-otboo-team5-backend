package com.codeit.otboo.api.common.mail;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 임시 비밀번호 생성기.
 * 영문 대소문자, 숫자, 특수문자를 각각 최소 1자 포함합니다.
 */
@Component
public class TempPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "!@#$%";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;

    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder();

        // 각 문자 종류를 최소 1자씩 보장
        sb.append(pick(UPPER));
        sb.append(pick(LOWER));
        sb.append(pick(DIGIT));
        sb.append(pick(SPECIAL));

        while (sb.length() < LENGTH) {
            sb.append(pick(ALL));
        }

        return shuffle(sb.toString());
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }

    private String shuffle(String value) {
        char[] chars = value.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}
