package com.codeit.otboo.api.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TempPasswordGeneratorTest {

    private final TempPasswordGenerator generator = new TempPasswordGenerator();

    /** 생성된 임시 비밀번호가 정해진 길이를 갖는지 확인합니다. */
    @Test
    void generatesPasswordWithFixedLength() {
        String password = generator.generate();

        assertThat(password).hasSize(10);
    }

    /** 영문 대소문자, 숫자, 특수문자를 각각 최소 1자 포함하는지 확인합니다. */
    @Test
    void includesAllRequiredCharacterTypes() {
        for (int i = 0; i < 100; i++) {
            String password = generator.generate();

            assertThat(password).matches(".*[A-Z].*");
            assertThat(password).matches(".*[a-z].*");
            assertThat(password).matches(".*[0-9].*");
            assertThat(password).matches(".*[!@#$%].*");
        }
    }

    /** 호출할 때마다 서로 다른 값을 생성하는지 확인합니다. */
    @Test
    void generatesDifferentPasswordEachTime() {
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            generated.add(generator.generate());
        }

        assertThat(generated).hasSizeGreaterThan(95);
    }
}
