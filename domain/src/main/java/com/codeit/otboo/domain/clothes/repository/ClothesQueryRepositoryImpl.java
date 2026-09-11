package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.entity.QClothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClothesRepositoryCustomImpl implements ClothesRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorResponse<Clothes> findAllByDynamicQuery(String cursor, UUID idAfter, Integer limit,
        ClothesCategory typeEqual, UUID ownerId) {
        QClothes clothes = QClothes.clothes;

        List<Clothes> content = queryFactory
            .selectFrom(clothes)
            .where(
                clothes.user.id.eq(ownerId),
                clothes.deletedAt.isNull(),
                typeEqualEq(clothes, typeEqual),
                cursorCondition(clothes, cursor, idAfter)
            )
            .orderBy(
                clothes.createdAt.desc(),
                clothes.id.desc()
            )
            .limit(limit + 1)
            .fetch();

        // 다음 페이지 존재 여부
        boolean hasNext = content.size() > limit;

        // limit + 1번째 데이터는 다음 페이지 존재 여부 확인용이므로 제거
        if (hasNext) {
            content.remove(content.size() - 1);
        }

        String nextCursor = null;
        UUID nextIdAfter = null;

        // 다음 페이지가 있다면 마지막 데이터의 정렬값을 다음 커서로 사용
        if (hasNext && !content.isEmpty()) {
            Clothes last = content.get(content.size() - 1);

            nextCursor = last.getCreatedAt().toString();
            nextIdAfter = last.getId();
        }

        // 전체 데이터 개수
        Long totalCount = queryFactory
            .select(clothes.count())
            .from(clothes)
            .where(
                clothes.user.id.eq(ownerId),
                clothes.deletedAt.isNull(),
                typeEqualEq(clothes, typeEqual)
            )
            .fetchOne();

        return new CursorResponse<>(
            content,
            nextCursor,
            nextIdAfter,
            hasNext,
            totalCount != null ? totalCount : 0L,
            "createdAt",
            "DESCENDING"
        );
    }

    private BooleanExpression typeEqualEq(
        QClothes clothes,
        ClothesCategory typeEqual
    ) {
        return typeEqual != null
            ? clothes.category.eq(typeEqual)
            : null;
    }

    private BooleanExpression cursorCondition(
        QClothes clothes,
        String cursor,
        UUID idAfter
    ) {
        if (cursor == null) {
            return null;
        }

        OffsetDateTime cursorCreatedAt =
            OffsetDateTime.parse(cursor);

        // idAfter가 없는 경우
        if (idAfter == null) {
            return clothes.createdAt.lt(cursorCreatedAt);
        }

        // createdAt DESC + id DESC 복합 커서
        return clothes.createdAt.lt(cursorCreatedAt)
            .or(
                clothes.createdAt.eq(cursorCreatedAt)
                    .and(clothes.id.lt(idAfter))
            );
    }
}