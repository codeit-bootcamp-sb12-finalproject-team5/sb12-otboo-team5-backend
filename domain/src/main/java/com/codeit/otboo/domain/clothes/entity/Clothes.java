package com.codeit.otboo.domain.clothes.entity;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clothes")
@Getter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothes extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 500)
	private String name;

	@Column(name = "is_owned", nullable = false)
	private Boolean isOwned;

	@Column(name = "preference")
	private Integer preference;

	@Setter
    @Column(name = "image_url", length = 1000)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false)
	private ClothesCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, length = 50)
	private ClothesGender gender;

	@Column(name = "brand", length = 255)
	private String brand;

	@Enumerated(EnumType.STRING)
	@Column(name = "season", nullable = false, length = 50)
	private ClothesSeason season;

	@Column(name = "attribute_text", columnDefinition = "TEXT")
	private String attributeText; // subcategory, color, fit, material, pattern, style

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Setter
	@JdbcTypeCode(SqlTypes.VECTOR)
	@Column(name = "attribute_vector", columnDefinition = "vector(1536)")
	private float[] attributeVector;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;


	public void update(
			String name,
			String brand,
			ClothesCategory category,
			ClothesSeason season,
			ClothesGender gender,
			String attributeText,
			String description,
			Boolean isOwned,
			Integer preference
	) {
		this.name = name;
		if (brand != null) {
			this.brand = brand;
		}
		this.category = category;
		if (season != null) {
			this.season = season;
		}
		if (gender != null) {
			this.gender = gender;
		}
		this.attributeText = attributeText;
		if (description != null) {
			this.description = description;
		}
		this.isOwned = isOwned;
		if (preference != null) {
			this.preference = preference;
		}
	}

}
