package com.socgen.bigdata.catalog.models.jpa.attribute;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "ATTRIBUTE_VALUE")
public class AttributeValue implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ENTITY_TYPE", nullable = false)
    private String objectType;

    @Column(name = "OBJECT_ID", nullable = false)
    private Long objectId;

    @Column(name = "VALUE", columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String value;

    @Generated(GenerationTime.INSERT)
    @Column(name = "CREATED_AT")
    private Date createdAt;

    @Generated(GenerationTime.ALWAYS)
    @Column(name = "UPDATED_AT")
    private Date updatedAt;
}
