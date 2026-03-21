package com.socgen.bigdata.catalog.models.dto.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /v2/api/object-types/{objectType}/{objectId}/attribute-values
 *
 * Returns all attribute values grouped by category.
 * Each attribute entry includes dataType and readOnly so the frontend
 * can build JSON Schema and control editability per field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeViewResponseDto {

    @JsonProperty("objectType")
    private String objectType;

    @JsonProperty("objectId")
    private Long objectId;

    @JsonProperty("categories")
    private List<CategoryDto> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDto {

        @JsonProperty("category")
        private String category;

        @JsonProperty("attributes")
        private List<AttributeEntryDto> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeEntryDto {

        @JsonProperty("attributeId")
        private Long attributeId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("technicalName")
        private String technicalName;

        /**
         * JSON Schema type string for JSON Forms rendering.
         * Values: "string" | "integer" | "number" | "boolean" | "array" | "object"
         */
        @JsonProperty("dataType")
        private String dataType;

        /**
         * If true — field is rendered as readonly in JSON Forms UI.
         * Driven by ATTRIBUTE.READ_ONLY column, not hardcoded on frontend.
         */
        @JsonProperty("readOnly")
        private Boolean readOnly;

        @JsonProperty("value")
        private Object value;
    }
}
