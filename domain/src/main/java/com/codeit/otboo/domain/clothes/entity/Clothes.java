package com.codeit.otboo.domain.clothes.entity;

import com.codeit.otboo.domain.common.SoftDeletableEntity;
import com.codeit.otboo.domain.profile.entity.Gender;
import com.codeit.otboo.domain.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
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
	private ClothingCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", nullable = false, length = 50)
	private Gender gender;

	@Column(name = "attribute_text", columnDefinition = "TEXT")
	private String attributeText;

	@JdbcTypeCode(SqlTypes.VECTOR)
	@Array(length = 768)
	@Column(name = "attribute_vector", columnDefinition = "vector(768)")
	private Float[] attributeVector;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

}
