package com.socgen.bigdata.catalog.models.dto.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeDefinitionResponseDto {

    @JsonProperty("objectType")
    private String objectType;

    @JsonProperty("category")
    private String category;

    @JsonProperty("createdOrUpdated")
    private List<CreatedAttributeDto> createdOrUpdated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedAttributeDto {

        @JsonProperty("attributeDefId")
        private Long attributeDefId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("technicalName")
        private String technicalName;
    }
}
