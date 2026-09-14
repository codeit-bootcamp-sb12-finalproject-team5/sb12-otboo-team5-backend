package com.codeit.otboo.domain.clothes.entity;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clothes")
@Getter @Setter @SuperBuilder @ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothes extends SoftDeletableEntity {

	@Column(name = "name", nullable = false, length = 500)
	private String name;

	@Column(name = "is_owned", nullable = false)
	private Boolean isOwned;

	@Column(name = "preference")
	private Integer preference;

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

	@JdbcTypeCode(SqlTypes.VECTOR)
	@Array(length = 1536)
	@Column(name = "attribute_vector", columnDefinition = "vector(1536)")
	private Float[] attributeVector;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
