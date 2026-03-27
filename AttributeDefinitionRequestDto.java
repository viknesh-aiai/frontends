package com.socgen.bigdata.catalog.models.dto.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeDefinitionRequestDto {

    @NotBlank(message = "category must not be blank")
    @JsonProperty("category")
    private String category;

    @NotEmpty(message = "attributes list must not be empty")
    @Valid
    @JsonProperty("attributes")
    private List<AttributeItemDto> attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeItemDto {

        @NotBlank(message = "name must not be blank")
        @JsonProperty("name")
        private String name;

        @NotBlank(message = "technicalName must not be blank")
        @JsonProperty("technicalName")
        private String technicalName;

        @NotBlank(message = "dataType must not be blank")
        @JsonProperty("dataType")
        private String dataType;

        @JsonProperty("readOnly")
        private Boolean readOnly;
    }
}
