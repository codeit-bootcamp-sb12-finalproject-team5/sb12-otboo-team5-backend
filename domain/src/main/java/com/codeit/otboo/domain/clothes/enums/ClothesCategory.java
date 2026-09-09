package com.codeit.otboo.domain.clothes.enums;

import lombok.Getter;

@Getter
public enum ClothesCategory implements Displayable {
	TOP("상의"),
	PANTS("바지"),
	SKIRT("스커트"),
	OUTER("아우터"),
	DRESS("원피스/세트"),
	HAT("모자"),
	SHOES("신발"),
	BAG("가방"),
	ACCESSORY("악세서리");

	private final String displayName;

	ClothesCategory(String displayName) {
		this.displayName = displayName;
	}

}
