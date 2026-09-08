package com.codeit.otboo.domain.profile.entity;

import lombok.Getter;

@Getter
public enum Gender {
	MALE("남성"),
	FEMALE("여성"),
	BOTH("기타");

	private final String description;

	Gender(String description) {
		this.description = description;
	}

}
