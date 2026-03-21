package com.socgen.bigdata.catalog.repositories.attribute;

import com.socgen.bigdata.catalog.models.jpa.attribute.Attribute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    List<Attribute> findAllByObjectType(String objectType);

    List<Attribute> findAllByObjectTypeAndCategory(String objectType, String category);

    /**
     * Find by technicalName — primary lookup key for value push.
     */
    Optional<Attribute> findByObjectTypeAndCategoryAndTechnicalName(
        String objectType,
        String category,
        String technicalName
    );

    /**
     * Find by display name (NAME column) — fallback lookup for value push.
     * Allows users to push values using either technicalName or display name.
     */
    Optional<Attribute> findByObjectTypeAndCategoryAndName(
        String objectType,
        String category,
        String name
    );
}
