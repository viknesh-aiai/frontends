package com.socgen.bigdata.catalog.models.dto.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for POST /v2/api/object-types/{objectType}/{objectId}/attribute-values
 *
 * Returns ONLY the attribute values that were pushed in this request.
 * The full stored map is not returned — only what was just upserted.
 *
 * values key = attribute_id as string, value = the pushed value.
 * e.g. if you pushed {"numberOfFiles": 21}, response is {"1": 21}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributeValueResponseDto {

    @JsonProperty("objectType")
    private String objectType;

    @JsonProperty("objectId")
    private Long objectId;

    @JsonProperty("category")
    private String category;

    /**
     * Only the values pushed in this request.
     * Key = attribute_id (as string), Value = pushed value.
     */
    @JsonProperty("values")
    private Map<String, Object> values;
}
