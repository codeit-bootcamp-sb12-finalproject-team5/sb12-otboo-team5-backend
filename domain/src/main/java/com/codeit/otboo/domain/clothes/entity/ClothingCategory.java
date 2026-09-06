package com.codeit.otboo.domain.clothes.entity;

import lombok.Getter;

@Getter
public enum ClothingCategory {
	TOPS("상의"),
	BOTTOMS("하의"),
	OUTER("아우터"),
	ONE_PIECES("원피스/세트"),
	HATS("모자"),
	SOCKS("양말"),
	SHOES("신발"),
	BAGS("가방"),
	ACCESSORIES("악세사리");

	private final String description;

	ClothingCategory(String description) {
		this.description = description;
	}

}
